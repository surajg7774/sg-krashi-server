package com.sgkrashi.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Fixed-window, in-memory rate limiter: at most {@code maxAttempts} calls to
 * {@link #tryConsume(String)} per key within the trailing {@code window}. Backed
 * by a plain {@link ConcurrentHashMap} of sliding attempt timestamps — sufficient
 * for a single-instance Year 1 deployment; move to a shared store (Redis) once the
 * app runs across multiple instances, since each instance would otherwise track
 * attempts independently. Used by both {@code LoginRateLimiter} (Module 2) and
 * {@code InquiryRateLimiter} (Module 3), each configuring their own limit/window.
 */
public class FixedWindowRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /**
     * @return true if the attempt is allowed (and is recorded), false if the caller
     * has exceeded the configured limit within the trailing window.
     */
    public boolean tryConsume(String key) {
        Instant now = Instant.now();
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (attempts) {
            Instant windowStart = now.minus(window);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(windowStart)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }
}
