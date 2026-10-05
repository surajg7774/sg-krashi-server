package com.sgkrashi.mandi.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * The two log lines this feature adds, one per sync attempt — kept in one
 * place so the exact wording (which people will grep Railway logs for) is
 * stated once and covered by tests. Key=value pairs, no stack traces, and
 * nothing that can carry the API key.
 */
final class MandiSyncMessages {

    private MandiSyncMessages() {
    }

    static String failed(String host, String reason, String trigger, Instant previousSuccess, Instant now,
                         int consecutiveFailedAttempts, int rowsUpsertedBeforeFailure) {
        return "Mandi sync FAILED: source=%s reason=%s trigger=%s lastSuccessfulSync=%s hoursSinceLastSuccess=%s "
                .formatted(host, reason, trigger, describeInstant(previousSuccess), hoursBetween(previousSuccess, now))
                + "consecutiveFailedAttempts=%d rowsUpsertedBeforeFailure=%d - retrying automatically"
                .formatted(consecutiveFailedAttempts, rowsUpsertedBeforeFailure);
    }

    static String recovered(String host, String trigger, int rowsUpserted, Instant previousSuccess, Instant now,
                            int failedAttemptsSinceRestart) {
        return "Mandi sync RECOVERED: source=%s is reachable again trigger=%s rowsUpserted=%d previousSuccessfulSync=%s "
                .formatted(host, trigger, rowsUpserted, describeInstant(previousSuccess))
                + "hoursSincePreviousSuccess=%s failedAttemptsSinceRestart=%d"
                .formatted(hoursBetween(previousSuccess, now), failedAttemptsSinceRestart);
    }

    private static String describeInstant(Instant instant) {
        return instant == null ? "never" : instant.toString();
    }

    private static String hoursBetween(Instant earlier, Instant later) {
        if (earlier == null) {
            return "n/a";
        }
        double hours = Duration.between(earlier, later).toMillis() / 3_600_000.0;
        return String.format(Locale.ROOT, "%.1f", hours);
    }
}
