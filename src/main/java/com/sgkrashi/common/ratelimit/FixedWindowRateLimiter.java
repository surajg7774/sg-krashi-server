package com.sgkrashi.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding-window, in-memory rate limiter: at most {@code maxAttempts} calls to
 * {@link #tryConsume(String)} per key within the trailing {@code window}. Backed
 * by a {@link ConcurrentHashMap} of attempt timestamps — sufficient for a
 * single-instance deployment; move to a shared store (Redis) once the app runs
 * across multiple instances, since each instance would otherwise track attempts
 * independently. Used by the login, auth-flow, inquiry, crop-doctor and chat limiters.
 *
 * <p>Memory is bounded: a key whose attempts have all left the window is dropped
 * by a sweep that runs at most once a minute (on the calling thread, no background
 * thread), and if more than {@code maxKeys} keys are live at once the least recently
 * used ones are dropped first (down to 90% of the cap). Dropping a key only ever forgives that key's history,
 * so a flood of distinct keys can cost the attacker nothing but can never grow the
 * map without limit.
 */
public class FixedWindowRateLimiter {

    /** Keys tracked at once before the least recently used are evicted. */
    public static final int DEFAULT_MAX_KEYS = 50_000;

    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    /** Outcome of one attempt: whether it is allowed, and if not how long until it could be. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final int maxKeys;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis;

    public FixedWindowRateLimiter(int maxAttempts, Duration window) {
        this(maxAttempts, window, Clock.systemUTC(), DEFAULT_MAX_KEYS);
    }

    public FixedWindowRateLimiter(int maxAttempts, Duration window, Clock clock, int maxKeys) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
        this.maxKeys = maxKeys;
        this.lastSweepMillis = new AtomicLong(clock.millis());
    }

    /**
     * @return true if the attempt is allowed (and is recorded), false if the caller
     * has exceeded the configured limit within the trailing window.
     */
    public boolean tryConsume(String key) {
        return acquire(key).allowed();
    }

    /** Same as {@link #tryConsume} but also says how many seconds until a denied caller may try again. */
    public Decision acquire(String key) {
        Instant now = clock.instant();
        sweepIfDue(now);

        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (attempts) {
            Instant windowStart = now.minus(window);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(windowStart)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                Instant freesUpAt = attempts.peekFirst().plus(window);
                long seconds = Math.max(1, (long) Math.ceil(Duration.between(now, freesUpAt).toMillis() / 1000.0));
                return new Decision(false, seconds);
            }
            attempts.addLast(now);
            return new Decision(true, 0);
        }
    }

    /** Number of keys currently tracked (for tests and diagnostics). */
    public int trackedKeys() {
        return attemptsByKey.size();
    }

    private void sweepIfDue(Instant now) {
        long nowMillis = now.toEpochMilli();
        long last = lastSweepMillis.get();
        boolean due = nowMillis - last >= SWEEP_INTERVAL.toMillis() || attemptsByKey.size() > maxKeys;
        if (!due || !lastSweepMillis.compareAndSet(last, nowMillis)) {
            return;
        }
        Instant windowStart = now.minus(window);
        // 1. Forget every key whose newest attempt has left the window.
        attemptsByKey.entrySet().removeIf(entry -> {
            Deque<Instant> attempts = entry.getValue();
            synchronized (attempts) {
                Instant newest = attempts.peekLast();
                return newest == null || newest.isBefore(windowStart);
            }
        });
        // 2. Still too many live keys: drop the least recently used until back under the cap.
        if (attemptsByKey.size() > maxKeys) {
            List<Map.Entry<String, Deque<Instant>>> byLastUse = new ArrayList<>(attemptsByKey.entrySet());
            byLastUse.sort(Comparator.comparing(entry -> {
                Instant newest = entry.getValue().peekLast();
                return newest == null ? Instant.MIN : newest;
            }));
            // Trim 10% below the cap, not just to it, so a sustained flood of new keys triggers this
            // O(n log n) pass once per ~10% of the cap rather than on every request.
            int target = Math.max(1, maxKeys - Math.max(1, maxKeys / 10));
            int toDrop = attemptsByKey.size() - target;
            for (int i = 0; i < toDrop && i < byLastUse.size(); i++) {
                attemptsByKey.remove(byLastUse.get(i).getKey());
            }
        }
    }
}
