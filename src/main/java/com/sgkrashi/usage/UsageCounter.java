package com.sgkrashi.usage;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Anonymous daily counters, kept in memory and written to {@code usage_daily} in batches.
 *
 * <p><b>Cheap and failure-safe.</b> {@link #record} only increments an in-memory number: no database call, no
 * lock, no exception. A request is never slowed or broken by counting; anything unexpected is swallowed.
 *
 * <p><b>Batching.</b> Every {@code app.usage-counters.flush-seconds} (default 30) and on a clean shutdown the
 * totals are added to the table with one additive upsert per (day, feature). If the database write fails the
 * amount is put back and retried at the next flush, so a database blip loses nothing. The map is bounded by
 * (days not yet written) x (11 features), so it cannot grow.
 *
 * <p><b>Honest limits.</b> If the server is killed without a clean shutdown (a crash, out-of-memory, power loss),
 * the counts since the last flush (at most one interval) are lost: a small undercount, never an overcount. A
 * deploy sends a normal shutdown, which flushes first.
 *
 * <p><b>Day.</b> Each count is filed under the India calendar day it happened on, whenever it is written.
 *
 * <p><b>Off switch.</b> {@code app.usage-counters.enabled=false} makes counting a no-op without a code change.
 */
@Component
public class UsageCounter {

    private static final Logger log = LoggerFactory.getLogger(UsageCounter.class);
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    /** A failing database is mentioned in the log at most this often, so a long outage cannot flood it. */
    private static final long WARN_EVERY_MILLIS = 10 * 60 * 1000L;

    private record Key(LocalDate day, UsageFeature feature) {
    }

    private final UsageDailyRepository repository;
    private final Clock clock;
    private final boolean enabled;
    private final Map<Key, AtomicLong> pending = new ConcurrentHashMap<>();
    private volatile long lastWarnAt = 0;

    @Autowired
    public UsageCounter(UsageDailyRepository repository, @Value("${app.usage-counters.enabled:true}") boolean enabled) {
        this(repository, Clock.system(INDIA), enabled);
    }

    UsageCounter(UsageDailyRepository repository, Clock clock, boolean enabled) {
        this.repository = repository;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** Counts one use of {@code feature} today. Never throws. */
    public void record(UsageFeature feature) {
        try {
            if (!enabled || feature == null) return;
            pending.computeIfAbsent(new Key(LocalDate.now(clock), feature), k -> new AtomicLong()).incrementAndGet();
        } catch (Throwable ignored) {
            // Counting must never affect a request.
        }
    }

    @Scheduled(fixedDelayString = "${app.usage-counters.flush-seconds:30}000", initialDelayString = "${app.usage-counters.flush-seconds:30}000")
    public void flush() {
        if (!enabled) return;
        for (Map.Entry<Key, AtomicLong> entry : pending.entrySet()) {
            long amount = entry.getValue().getAndSet(0);
            if (amount == 0) {
                // Past days with nothing left to write can go; today's entry is kept (it is cheap and avoids churn).
                if (entry.getKey().day().isBefore(LocalDate.now(clock))) pending.remove(entry.getKey(), entry.getValue());
                continue;
            }
            try {
                repository.add(entry.getKey().day(), entry.getKey().feature().key(), amount);
            } catch (Throwable failure) {
                entry.getValue().addAndGet(amount); // keep it for the next flush
                warnRarely(failure);
            }
        }
    }

    /** A clean shutdown writes what is still in memory. */
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    /** Counts waiting to be written (tests and diagnostics only). */
    long pendingTotal() {
        return pending.values().stream().mapToLong(AtomicLong::get).sum();
    }

    private void warnRarely(Throwable failure) {
        long now = System.currentTimeMillis();
        if (now - lastWarnAt >= WARN_EVERY_MILLIS) {
            lastWarnAt = now;
            log.warn("Usage counters could not be written (will retry): {}", failure.getClass().getSimpleName());
        }
    }
}
