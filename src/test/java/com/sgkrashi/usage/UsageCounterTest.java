package com.sgkrashi.usage;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UsageCounterTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private volatile Instant now;

        MovableClock(String instant) {
            this.now = Instant.parse(instant);
        }

        void set(String instant) {
            this.now = Instant.parse(instant);
        }

        @Override public ZoneId getZone() { return INDIA; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Collects what would have been written to usage_daily. */
    private static final class Ledger {
        final Map<String, AtomicLong> totals = new ConcurrentHashMap<>();
        final UsageDailyRepository repository = mock(UsageDailyRepository.class);

        Ledger() {
            doAnswer(call -> {
                totals.computeIfAbsent(call.getArgument(0) + "|" + call.getArgument(1), k -> new AtomicLong()).addAndGet(call.getArgument(2));
                return null;
            }).when(repository).add(any(), anyString(), anyLong());
        }

        long total(String day, UsageFeature f) {
            AtomicLong n = totals.get(day + "|" + f.key());
            return n == null ? 0 : n.get();
        }
    }

    private static UsageCounter counter(Ledger ledger, MovableClock clock) {
        return new UsageCounter(ledger.repository, clock, true);
    }

    @Test
    void manyCallsBecomeOneBatchedWritePerFeaturePerDay() {
        Ledger ledger = new Ledger();
        UsageCounter counter = counter(ledger, new MovableClock("2026-10-06T08:00:00Z"));
        for (int i = 0; i < 500; i++) counter.record(UsageFeature.PRODUCT_DETAIL);
        for (int i = 0; i < 20; i++) counter.record(UsageFeature.MANDI_PRICES);

        verify(ledger.repository, never()).add(any(), anyString(), anyLong()); // nothing touches the database while recording
        counter.flush();

        verify(ledger.repository, times(2)).add(any(), anyString(), anyLong());
        assertEquals(500, ledger.total("2026-10-06", UsageFeature.PRODUCT_DETAIL));
        assertEquals(20, ledger.total("2026-10-06", UsageFeature.MANDI_PRICES));
        assertEquals(0, counter.pendingTotal());
    }

    @Test
    void aSecondFlushOnlyWritesWhatIsNew() {
        Ledger ledger = new Ledger();
        UsageCounter counter = counter(ledger, new MovableClock("2026-10-06T08:00:00Z"));
        counter.record(UsageFeature.WEATHER);
        counter.flush();
        counter.flush(); // nothing new
        counter.record(UsageFeature.WEATHER);
        counter.record(UsageFeature.WEATHER);
        counter.flush();
        assertEquals(3, ledger.total("2026-10-06", UsageFeature.WEATHER));
        verify(ledger.repository, times(2)).add(any(), anyString(), anyLong());
    }

    @Test
    void countsAreFiledUnderTheIndiaDayTheyHappenedOn() {
        Ledger ledger = new Ledger();
        MovableClock clock = new MovableClock("2026-10-06T18:29:00Z"); // 23:59 in India on 6 Oct
        UsageCounter counter = counter(ledger, clock);
        counter.record(UsageFeature.SCHEMES);
        clock.set("2026-10-06T18:31:00Z");                              // 00:01 in India on 7 Oct (still 6 Oct in UTC)
        counter.record(UsageFeature.SCHEMES);
        counter.flush();                                                // written later, still on the right days
        assertEquals(1, ledger.total("2026-10-06", UsageFeature.SCHEMES));
        assertEquals(1, ledger.total("2026-10-07", UsageFeature.SCHEMES));
    }

    @Test
    void aFailedWriteLosesNothingAndIsRetried() {
        UsageDailyRepository repository = mock(UsageDailyRepository.class);
        AtomicBoolean failing = new AtomicBoolean(true);
        AtomicLong written = new AtomicLong();
        doAnswer(call -> {
            if (failing.get()) throw new IllegalStateException("database is down");
            written.addAndGet(call.getArgument(2));
            return null;
        }).when(repository).add(any(), anyString(), anyLong());
        UsageCounter counter = new UsageCounter(repository, new MovableClock("2026-10-06T08:00:00Z"), true);

        for (int i = 0; i < 40; i++) counter.record(UsageFeature.ADD_TO_CART);
        counter.flush(); // fails: must not throw, must keep the 40
        counter.flush(); // fails again
        assertEquals(40, counter.pendingTotal());
        assertEquals(0, written.get());

        counter.record(UsageFeature.ADD_TO_CART);
        failing.set(false);
        counter.flush();
        assertEquals(41, written.get());
        assertEquals(0, counter.pendingTotal());
    }

    @Test
    void recordNeverThrowsEvenForNullAndNeverWritesWhenDisabled() {
        Ledger ledger = new Ledger();
        UsageCounter on = counter(ledger, new MovableClock("2026-10-06T08:00:00Z"));
        on.record(null);
        assertEquals(0, on.pendingTotal());

        UsageCounter off = new UsageCounter(ledger.repository, new MovableClock("2026-10-06T08:00:00Z"), false);
        off.record(UsageFeature.WEATHER);
        off.flush();
        assertEquals(0, off.pendingTotal());
        verify(ledger.repository, never()).add(any(), anyString(), anyLong());
    }

    @Test
    void aCleanShutdownWritesWhatIsStillInMemory() {
        Ledger ledger = new Ledger();
        UsageCounter counter = counter(ledger, new MovableClock("2026-10-06T08:00:00Z"));
        counter.record(UsageFeature.STAY_DETAIL);
        counter.record(UsageFeature.STAY_DETAIL);
        counter.flushOnShutdown();
        assertEquals(2, ledger.total("2026-10-06", UsageFeature.STAY_DETAIL));
    }

    @Test
    void aCrashLosesAtMostWhatWasNotYetWritten() {
        Ledger ledger = new Ledger();
        UsageCounter before = counter(ledger, new MovableClock("2026-10-06T08:00:00Z"));
        before.record(UsageFeature.PRODUCT_DETAIL);
        before.flush();                                    // written
        before.record(UsageFeature.PRODUCT_DETAIL);        // in memory only: lost if the process is killed here
        UsageCounter afterRestart = counter(ledger, new MovableClock("2026-10-06T08:05:00Z")); // a new process starts with an empty map
        afterRestart.flush();
        assertEquals(1, ledger.total("2026-10-06", UsageFeature.PRODUCT_DETAIL), "the written count survives, the unwritten one is gone: an undercount, never an overcount");
    }

    @Test
    void manyThreadsRecordingWhileFlushingNeverLoseOrInventACount() throws Exception {
        Ledger ledger = new Ledger();
        UsageCounter counter = counter(ledger, new MovableClock("2026-10-06T08:00:00Z"));
        int threads = 8, perThread = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) counter.record(i % 2 == 0 ? UsageFeature.PRODUCT_DETAIL : UsageFeature.WEATHER);
            });
        }
        pool.submit(() -> {
            for (int i = 0; i < 200; i++) counter.flush(); // flushes racing the writers
        });
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        counter.flush();
        assertEquals((long) threads * perThread / 2, ledger.total("2026-10-06", UsageFeature.PRODUCT_DETAIL));
        assertEquals((long) threads * perThread / 2, ledger.total("2026-10-06", UsageFeature.WEATHER));
    }

    @Test
    void aFailingRepositoryExceptionIsNeverRethrownFromFlush() {
        UsageDailyRepository repository = mock(UsageDailyRepository.class);
        doThrow(new OutOfMemoryError("simulated")).when(repository).add(any(), anyString(), anyLong());
        UsageCounter counter = new UsageCounter(repository, Clock.fixed(Instant.parse("2026-10-06T08:00:00Z"), ZoneOffset.UTC), true);
        counter.record(UsageFeature.SCHEMES);
        counter.flush(); // must not throw (this also guards the scheduler thread from dying)
        assertEquals(1, counter.pendingTotal());
    }
}
