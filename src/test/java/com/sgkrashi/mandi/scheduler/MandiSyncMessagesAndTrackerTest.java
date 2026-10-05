package com.sgkrashi.mandi.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MandiSyncMessagesAndTrackerTest {

    private static final Instant NOW = Instant.parse("2026-10-05T07:00:00Z");

    @Test
    void failedLineHasTheExactDocumentedShape() {
        String line = MandiSyncMessages.failed("api.data.gov.in", "unreachable (connection refused)", "scheduled-0630-IST",
                NOW.minus(Duration.ofHours(50)).minus(Duration.ofMinutes(30)), NOW, 3, 0);

        assertEquals("Mandi sync FAILED: source=api.data.gov.in reason=unreachable (connection refused) trigger=scheduled-0630-IST "
                + "lastSuccessfulSync=2026-10-03T04:30:00Z hoursSinceLastSuccess=50.5 "
                + "consecutiveFailedAttempts=3 rowsUpsertedBeforeFailure=0 - retrying automatically", line);
    }

    @Test
    void failedLineForANeverSyncedTable() {
        String line = MandiSyncMessages.failed("api.data.gov.in", "HTTP 503", "manual", null, NOW, 1, 0);

        assertTrue(line.contains("lastSuccessfulSync=never hoursSinceLastSuccess=n/a"), line);
    }

    @Test
    void recoveredLineHasTheExactDocumentedShape() {
        String line = MandiSyncMessages.recovered("api.data.gov.in", "catch-up", 1234, NOW.minus(Duration.ofHours(72)), NOW, 4);

        assertEquals("Mandi sync RECOVERED: source=api.data.gov.in is reachable again trigger=catch-up rowsUpserted=1234 "
                + "previousSuccessfulSync=2026-10-02T07:00:00Z hoursSincePreviousSuccess=72.0 failedAttemptsSinceRestart=4", line);
    }

    @Test
    void trackerCountsConsecutiveFailuresAndRemembersWhenTheyStarted() {
        MandiSyncTracker tracker = new MandiSyncTracker();
        assertFalse(tracker.previousAttemptFailed());
        assertNull(tracker.lastSuccessAt());

        assertEquals(1, tracker.recordFailure(NOW));
        assertEquals(2, tracker.recordFailure(NOW.plus(Duration.ofHours(6))));
        assertTrue(tracker.previousAttemptFailed());

        Optional<MandiSyncTracker.Recovery> recovery = tracker.recordSuccess(NOW.plus(Duration.ofHours(12)));
        assertTrue(recovery.isPresent());
        assertEquals(2, recovery.get().failedAttempts());
        assertEquals(Duration.ofHours(12), recovery.get().outage());
        assertFalse(tracker.previousAttemptFailed());
        assertEquals(NOW.plus(Duration.ofHours(12)), tracker.lastSuccessAt());
    }

    @Test
    void aSuccessWithNoPriorFailureIsNotARecovery() {
        MandiSyncTracker tracker = new MandiSyncTracker();

        assertTrue(tracker.recordSuccess(NOW).isEmpty());
        assertEquals(NOW, tracker.lastSuccessAt());
    }

    @Test
    void aNewFailureAfterARecoveryStartsAFreshOutage() {
        MandiSyncTracker tracker = new MandiSyncTracker();
        tracker.recordFailure(NOW);
        tracker.recordSuccess(NOW.plus(Duration.ofHours(1)));

        assertEquals(1, tracker.recordFailure(NOW.plus(Duration.ofHours(5))));
        Optional<MandiSyncTracker.Recovery> recovery = tracker.recordSuccess(NOW.plus(Duration.ofHours(7)));

        assertEquals(Duration.ofHours(2), recovery.orElseThrow().outage());
    }
}
