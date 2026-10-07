package com.sgkrashi.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTest {

    /** A clock the test moves by hand. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    private FixedWindowRateLimiter limiter(int max, Duration window, int maxKeys) {
        return new FixedWindowRateLimiter(max, window, clock, maxKeys);
    }

    @Test
    void allowsUpToTheLimitThenDeniesAndSaysWhenToRetry() {
        FixedWindowRateLimiter limiter = limiter(3, Duration.ofMinutes(10), 100);
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.acquire("a").allowed());
            clock.advance(Duration.ofSeconds(10));
        }
        FixedWindowRateLimiter.Decision denied = limiter.acquire("a");
        assertFalse(denied.allowed());
        // The oldest attempt was 30s ago in a 600s window: it frees up in 570s.
        assertEquals(570, denied.retryAfterSeconds());
        assertFalse(limiter.tryConsume("a"));
    }

    @Test
    void theCallerMayTryAgainOnceTheOldestAttemptLeavesTheWindow() {
        FixedWindowRateLimiter limiter = limiter(2, Duration.ofMinutes(1), 100);
        assertTrue(limiter.tryConsume("a"));
        assertTrue(limiter.tryConsume("a"));
        assertFalse(limiter.tryConsume("a"));
        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.tryConsume("a"));
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ofSeconds(30), 100);
        assertTrue(limiter.tryConsume("a"));
        clock.advance(Duration.ofMillis(29_900));
        FixedWindowRateLimiter.Decision denied = limiter.acquire("a");
        assertFalse(denied.allowed());
        assertEquals(1, denied.retryAfterSeconds());
    }

    @Test
    void keysAreIndependent() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ofMinutes(1), 100);
        assertTrue(limiter.tryConsume("a"));
        assertFalse(limiter.tryConsume("a"));
        assertTrue(limiter.tryConsume("b"));
    }

    @Test
    void keysWhoseAttemptsHaveAllExpiredAreForgottenSoMemoryDoesNotGrow() {
        FixedWindowRateLimiter limiter = limiter(5, Duration.ofMinutes(15), 100_000);
        for (int i = 0; i < 1_000; i++) {
            limiter.tryConsume("ip-" + i);
        }
        assertEquals(1_000, limiter.trackedKeys());

        clock.advance(Duration.ofMinutes(16)); // every attempt is now outside the window
        limiter.tryConsume("fresh");           // the next call sweeps

        assertEquals(1, limiter.trackedKeys(), "only the fresh key should be left");
    }

    @Test
    void aFloodOfDistinctKeysCannotGrowTheMapPastItsCap() {
        int cap = 500;
        FixedWindowRateLimiter limiter = limiter(5, Duration.ofHours(1), cap);
        for (int i = 0; i < 20_000; i++) {
            limiter.tryConsume("spoofed-" + i);
            clock.advance(Duration.ofMillis(5));
        }
        assertTrue(limiter.trackedKeys() <= cap + 1,
                "tracked " + limiter.trackedKeys() + " keys, cap is " + cap);
    }

    @Test
    void whenTrimmingToTheCapTheMostRecentlyActiveKeysSurvive() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ofHours(1), 3);
        limiter.tryConsume("old-1");
        clock.advance(Duration.ofSeconds(1));
        limiter.tryConsume("old-2");
        clock.advance(Duration.ofSeconds(1));
        limiter.tryConsume("recent-1");
        clock.advance(Duration.ofSeconds(1));
        limiter.tryConsume("recent-2");  // 4 keys > cap 3
        clock.advance(Duration.ofSeconds(1));
        limiter.tryConsume("recent-3");  // triggers the trim

        // The recent key is still limited (its history survived)...
        assertFalse(limiter.tryConsume("recent-3"));
        // ...while the least recently used was forgiven.
        assertTrue(limiter.tryConsume("old-1"));
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowRateLimiter(0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(1, Duration.ofMinutes(1), Clock.systemUTC(), 0));
    }
}
