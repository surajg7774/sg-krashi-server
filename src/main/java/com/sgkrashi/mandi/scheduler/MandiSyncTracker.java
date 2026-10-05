package com.sgkrashi.mandi.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * In-memory memory of how the last few mandi sync attempts went, so the
 * catch-up schedule knows whether "the previous attempt failed" and the
 * recovery log line knows how long the outage lasted. Deliberately not
 * persisted: it only needs to survive between today's attempts, and after a
 * restart the job falls back to the table itself (see {@code
 * MandiPriceSyncJob}'s freshness check), which is persistent.
 */
final class MandiSyncTracker {

    private int consecutiveFailures;
    private Instant firstFailureAt;
    private Instant lastSuccessAt;

    synchronized boolean previousAttemptFailed() {
        return consecutiveFailures > 0;
    }

    /** The last attempt this process saw succeed, or null. Counts even when the data it fetched was identical to what was stored. */
    synchronized Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    /** @return the consecutive-failure count including this one */
    synchronized int recordFailure(Instant now) {
        if (consecutiveFailures == 0) {
            firstFailureAt = now;
        }
        return ++consecutiveFailures;
    }

    /** @return details of the outage this success ends, or empty if no failure was being tracked */
    synchronized Optional<Recovery> recordSuccess(Instant now) {
        Optional<Recovery> recovery = consecutiveFailures > 0
                ? Optional.of(new Recovery(consecutiveFailures, Duration.between(firstFailureAt, now)))
                : Optional.empty();
        consecutiveFailures = 0;
        firstFailureAt = null;
        lastSuccessAt = now;
        return recovery;
    }

    record Recovery(int failedAttempts, Duration outage) {
    }
}
