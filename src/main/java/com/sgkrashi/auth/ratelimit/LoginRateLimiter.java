package com.sgkrashi.auth.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Fixed-window, in-memory rate limiter for {@code /auth/login} and {@code /auth/register}
 * (max 5 attempts per 15 minutes per client IP). This is a plain {@link ConcurrentHashMap}
 * of sliding attempt timestamps rather than a library like Bucket4j — sufficient for a
 * single-instance Year 1 deployment, and avoids pulling in a dependency + shared cache
 * (Redis) before the platform actually needs to scale horizontally. If/when the app runs
 * across multiple instances, this must move to a shared store (Redis) since each instance
 * would otherwise track attempts independently.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    /**
     * @return true if the attempt is allowed (and is recorded), false if the caller
     * has exceeded {@value #MAX_ATTEMPTS} attempts within the trailing 15-minute window.
     */
    public boolean tryConsume(String key) {
        Instant now = Instant.now();
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (attempts) {
            Instant windowStart = now.minus(WINDOW);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(windowStart)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_ATTEMPTS) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }
}
