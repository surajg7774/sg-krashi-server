package com.sgkrashi.common.health;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseHealthCheckerTest {

    private final MutableClock clock = new MutableClock();
    private DatabaseHealthChecker checker;

    @AfterEach
    void tearDown() {
        if (checker != null) {
            checker.shutdown();
        }
    }

    private DatabaseHealthChecker checkerWith(BooleanSupplier probe, Duration timeout) {
        checker = new DatabaseHealthChecker(probe, clock, timeout);
        return checker;
    }

    @Test
    void upWhenTheProbeSucceeds() {
        assertTrue(checkerWith(() -> true, Duration.ofSeconds(1)).isDatabaseUp());
    }

    @Test
    void downWhenTheProbeSaysNo() {
        assertFalse(checkerWith(() -> false, Duration.ofSeconds(1)).isDatabaseUp());
    }

    @Test
    void downWhenTheProbeThrows() {
        assertFalse(checkerWith(() -> {
            throw new IllegalStateException("jdbc:mysql://secret-host:3306/db refused the connection");
        }, Duration.ofSeconds(1)).isDatabaseUp());
    }

    @Test
    void downAndFastWhenTheProbeHangs() {
        DatabaseHealthChecker hung = checkerWith(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return true;
        }, Duration.ofMillis(200));

        long start = System.nanoTime();
        boolean up = hung.isDatabaseUp();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(up);
        assertTrue(elapsedMs < 2_000, "returned after " + elapsedMs + " ms instead of at the timeout");
    }

    @Test
    void repeatedCallsWithinTheWindowCostOneProbe() {
        AtomicInteger calls = new AtomicInteger();
        DatabaseHealthChecker cached = checkerWith(() -> {
            calls.incrementAndGet();
            return true;
        }, Duration.ofSeconds(1));

        for (int i = 0; i < 50; i++) {
            assertTrue(cached.isDatabaseUp());
        }
        assertEquals(1, calls.get());
    }

    @Test
    void probesAgainOnceTheWindowHasPassed() {
        AtomicInteger calls = new AtomicInteger();
        DatabaseHealthChecker refreshed = checkerWith(() -> calls.incrementAndGet() == 1, Duration.ofSeconds(1));

        assertTrue(refreshed.isDatabaseUp());
        clock.advance(DatabaseHealthChecker.CACHE_TTL.plusSeconds(1));
        assertFalse(refreshed.isDatabaseUp(), "an outage after a healthy check must show up once the cache expires");
        assertEquals(2, calls.get());
    }

    @Test
    void aDownResultIsAlsoCachedSoAnOutageIsNotHammered() {
        AtomicInteger calls = new AtomicInteger();
        DatabaseHealthChecker down = checkerWith(() -> {
            calls.incrementAndGet();
            return false;
        }, Duration.ofSeconds(1));

        down.isDatabaseUp();
        down.isDatabaseUp();
        down.isDatabaseUp();
        assertEquals(1, calls.get());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T00:00:00Z");

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
}
