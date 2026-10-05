package com.sgkrashi.mandi.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sgkrashi.mandi.client.MandiApiNotConfiguredException;
import com.sgkrashi.mandi.client.MandiApiUnavailableException;
import com.sgkrashi.mandi.client.MandiPriceApiClient;
import com.sgkrashi.mandi.client.MandiPriceRow;
import com.sgkrashi.mandi.repository.MandiPriceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No Spring context, no network, no database: the API client and repository
 * are mocks and the clock is controlled. A fake API key is embedded in the
 * simulated failure's request URL to prove it can never reach a log line.
 */
class MandiPriceSyncJobTest {

    // 7 priority commodities + 1 unfiltered pass = 8 fetchAll calls per full attempt.
    private static final int CALLS_PER_FULL_ATTEMPT = 8;
    private static final String FAKE_KEY = "SECRET-KEY-123";

    private final MandiPriceApiClient client = mock(MandiPriceApiClient.class);
    private final MandiPriceRepository repository = mock(MandiPriceRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T07:00:00Z"));
    private MandiPriceSyncJob job;
    private ListAppender<ILoggingEvent> logs;
    private Logger jobLogger;

    @BeforeEach
    void setUp() {
        when(client.isConfigured()).thenReturn(true);
        when(client.getHost()).thenReturn("api.data.gov.in");
        when(repository.findLastSyncedAt()).thenReturn(Optional.empty());
        job = new MandiPriceSyncJob(client, repository, clock);

        jobLogger = (Logger) LoggerFactory.getLogger(MandiPriceSyncJob.class);
        logs = new ListAppender<>();
        logs.start();
        jobLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        jobLogger.detachAppender(logs);
    }

    // ---- helpers -----------------------------------------------------------------

    // doThrow/doReturn (not when(...)): these are called again mid-test to flip the API's state,
    // and when(client.fetchAll(...)) would invoke the mock and trip over the previous stub.
    private void apiDown() {
        doThrow(refused()).when(client).fetchAll(any(), any());
    }

    private void apiUp() {
        MandiPriceRow row = new MandiPriceRow("Wheat", "Indore", "Madhya Pradesh", "Indore",
                new BigDecimal("2000"), new BigDecimal("2200"), new BigDecimal("2100"), LocalDate.of(2026, 10, 4));
        doReturn(List.of(row)).when(client).fetchAll(any(), any());
    }

    private static MandiApiUnavailableException refused() {
        return new MandiApiUnavailableException("Mandi price API is unreachable",
                new WebClientRequestException(
                        new ConnectException("finishConnect(..) failed with error(-111): Connection refused: api.data.gov.in/164.100.61.198:443"),
                        HttpMethod.GET,
                        URI.create("https://api.data.gov.in/resource/9ef84268?api-key=" + FAKE_KEY + "&format=json&limit=500"),
                        HttpHeaders.EMPTY));
    }

    private void tableLastChanged(Duration ago) {
        when(repository.findLastSyncedAt()).thenReturn(Optional.of(clock.instant().minus(ago)));
    }

    private List<String> messagesAtLeast(Level level) {
        return logs.list.stream().filter(e -> e.getLevel().isGreaterOrEqual(level)).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ---- the scheduling guard ----------------------------------------------------

    @Test
    void catchUpDoesNothingWhenDataIsFreshAndNothingFailed() {
        tableLastChanged(Duration.ofHours(6));

        job.syncCatchUp();

        verify(client, never()).fetchAll(any(), any());
        assertTrue(messagesAtLeast(Level.INFO).isEmpty(), "a skipped catch-up must not log at INFO or above");
    }

    @Test
    void catchUpRunsWhenTheTableHasNoRowsAtAll() {
        apiUp();

        job.syncCatchUp();

        verify(client, times(CALLS_PER_FULL_ATTEMPT)).fetchAll(any(), any());
    }

    @Test
    void catchUpRunsWhenTheNewestRowChangeIsOlderThan24Hours() {
        tableLastChanged(Duration.ofHours(25));
        apiUp();

        job.syncCatchUp();

        verify(client, times(CALLS_PER_FULL_ATTEMPT)).fetchAll(any(), any());
    }

    @Test
    void catchUpStillSkipsJustInsideTheFreshnessWindow() {
        tableLastChanged(Duration.ofHours(23).plusMinutes(59));

        job.syncCatchUp();

        verify(client, never()).fetchAll(any(), any());
    }

    @Test
    void catchUpRunsAfterAFailedAttemptEvenThoughTheTableLooksFresh() {
        tableLastChanged(Duration.ofHours(2));
        apiDown();
        job.syncDaily(); // fails on its first call
        verify(client, times(1)).fetchAll(any(), any());

        apiUp();
        clock.advance(Duration.ofHours(6));
        job.syncCatchUp();

        verify(client, times(1 + CALLS_PER_FULL_ATTEMPT)).fetchAll(any(), any());
    }

    @Test
    void afterACatchUpSucceedsTheNextCatchUpSkips() {
        apiDown();
        job.syncDaily();
        apiUp();
        clock.advance(Duration.ofHours(6));
        job.syncCatchUp(); // recovers
        int callsSoFar = 1 + CALLS_PER_FULL_ATTEMPT;
        verify(client, times(callsSoFar)).fetchAll(any(), any());

        clock.advance(Duration.ofHours(6));
        job.syncCatchUp(); // 18:30 — everything is fine now

        verify(client, times(callsSoFar)).fetchAll(any(), any());
    }

    @Test
    void aSuccessfulSyncOfUnchangedDataStillCountsAsFresh() {
        // Re-upserting identical rows doesn't touch updated_at, so the table can look
        // old even though the sync just succeeded — the job's own memory covers that.
        tableLastChanged(Duration.ofHours(30));
        apiUp();
        job.syncDaily();
        verify(client, times(CALLS_PER_FULL_ATTEMPT)).fetchAll(any(), any());

        clock.advance(Duration.ofHours(6));
        job.syncCatchUp();

        verify(client, times(CALLS_PER_FULL_ATTEMPT)).fetchAll(any(), any());
    }

    @Test
    void catchUpDoesNothingWhenNoApiKeyIsConfigured() {
        when(client.isConfigured()).thenReturn(false);

        job.syncCatchUp();

        verify(client, never()).fetchAll(any(), any());
    }

    @Test
    void theDailyAttemptAlwaysRunsEvenWhenDataIsFresh() {
        tableLastChanged(Duration.ofHours(1));
        apiUp();

        job.syncDaily();

        verify(client, times(CALLS_PER_FULL_ATTEMPT)).fetchAll(any(), any());
    }

    // ---- failure logging ---------------------------------------------------------

    @Test
    void aFailedAttemptLogsExactlyOneWarnWithHostReasonAndHoursSinceLastSuccess() {
        tableLastChanged(Duration.ofHours(50));
        apiDown();

        int upserted = job.runOnce();

        assertEquals(0, upserted, "a manual run against a dead API returns 0 and does not throw");
        List<String> warns = warnings();
        assertEquals(1, warns.size(), "one WARN per failed attempt, got: " + warns);
        String line = warns.get(0);
        assertTrue(line.contains("Mandi sync FAILED"), line);
        assertTrue(line.contains("source=api.data.gov.in"), line);
        assertTrue(line.contains("reason=unreachable (connection refused)"), line);
        assertTrue(line.contains("trigger=manual"), line);
        assertTrue(line.contains("hoursSinceLastSuccess=50.0"), line);
        assertTrue(line.contains("consecutiveFailedAttempts=1"), line);
        assertTrue(line.contains("retrying automatically"), line);
    }

    @Test
    void neverSyncedIsReportedAsNeverNotAsAHugeNumber() {
        apiDown();

        job.runOnce();

        String line = warnings().get(0);
        assertTrue(line.contains("lastSuccessfulSync=never"), line);
        assertTrue(line.contains("hoursSinceLastSuccess=n/a"), line);
    }

    @Test
    void theApiKeyNeverAppearsInAnyLogLine() {
        apiDown();

        job.runOnce();
        job.runOnce();

        for (ILoggingEvent event : logs.list) {
            String text = event.getFormattedMessage() + " " + event.getThrowableProxy();
            assertFalse(text.contains(FAKE_KEY), "key leaked into: " + text);
            assertFalse(text.contains("api-key"), "request URL leaked into: " + text);
        }
    }

    @Test
    void eachFailedAttemptLogsOneLineAndCountsUp() {
        apiDown();

        job.syncDaily();
        clock.advance(Duration.ofHours(6));
        job.syncCatchUp();
        clock.advance(Duration.ofHours(6));
        job.syncCatchUp();

        List<String> warns = warnings();
        assertEquals(3, warns.size());
        assertTrue(warns.get(0).contains("consecutiveFailedAttempts=1") && warns.get(0).contains("trigger=scheduled-0630-IST"));
        assertTrue(warns.get(1).contains("consecutiveFailedAttempts=2") && warns.get(1).contains("trigger=catch-up"));
        assertTrue(warns.get(2).contains("consecutiveFailedAttempts=3"));
    }

    @Test
    void aMissingApiKeyIsAnInfoSkipNotAFailure() {
        when(client.fetchAll(any(), any())).thenThrow(new MandiApiNotConfiguredException("MANDI_API_KEY is not set"));

        job.syncDaily();

        assertTrue(warnings().isEmpty());
        assertTrue(messagesAtLeast(Level.INFO).get(0).contains("skipped"));
    }

    @Test
    void aDatabaseHiccupWhileReadingLastSyncDoesNotBreakTheFailureLog() {
        when(repository.findLastSyncedAt()).thenThrow(new IllegalStateException("db down"));
        apiDown();

        job.runOnce();

        assertEquals(1, warnings().size());
        assertTrue(warnings().get(0).contains("lastSuccessfulSync=never"));
    }

    // ---- recovery logging --------------------------------------------------------

    @Test
    void recoveryAfterFailuresLogsOneInfoLineWithTheRowCountAndNoOrdinaryRanLine() {
        apiDown();
        job.syncDaily();
        clock.advance(Duration.ofHours(6));
        job.syncCatchUp();

        apiUp();
        clock.advance(Duration.ofHours(6));
        job.syncCatchUp();

        List<String> infos = logs.list.stream().filter(e -> e.getLevel() == Level.INFO).map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(1, infos.size(), "exactly one INFO for the recovering attempt, got: " + infos);
        String line = infos.get(0);
        assertTrue(line.contains("Mandi sync RECOVERED"), line);
        assertTrue(line.contains("source=api.data.gov.in is reachable again"), line);
        assertTrue(line.contains("rowsUpserted=" + CALLS_PER_FULL_ATTEMPT), line);
        assertTrue(line.contains("failedAttemptsSinceRestart=2"), line);
        assertTrue(line.contains("trigger=catch-up"), line);
        assertFalse(infos.stream().anyMatch(m -> m.contains("MandiPriceSyncJob ran")));
    }

    @Test
    void theAttemptAfterARecoveryIsAnOrdinarySuccessLine() {
        apiDown();
        job.syncDaily();
        apiUp();
        job.runOnce(); // recovers
        logs.list.clear();

        job.runOnce();

        List<String> infos = messagesAtLeast(Level.INFO);
        assertEquals(1, infos.size());
        assertTrue(infos.get(0).startsWith("MandiPriceSyncJob ran: upserted " + CALLS_PER_FULL_ATTEMPT));
    }

    @Test
    void aRestartBetweenFailureAndRecoveryIsStillReportedBecauseTheTableShowsTheGap() {
        // Fresh process: the tracker remembers nothing, but the table's last change is 60h old.
        tableLastChanged(Duration.ofHours(60));
        apiUp();

        job.syncDaily();

        String line = messagesAtLeast(Level.INFO).get(0);
        assertTrue(line.contains("Mandi sync RECOVERED"), line);
        assertTrue(line.contains("hoursSincePreviousSuccess=60.0"), line);
        assertTrue(line.contains("failedAttemptsSinceRestart=0"), line);
    }

    @Test
    void aSuccessOnFreshDataLogsTheOrdinaryLineNotARecovery() {
        tableLastChanged(Duration.ofHours(5));
        apiUp();

        job.syncDaily();

        List<String> infos = messagesAtLeast(Level.INFO);
        assertEquals(1, infos.size());
        assertEquals("MandiPriceSyncJob ran: upserted " + CALLS_PER_FULL_ATTEMPT + " row(s)", infos.get(0));
    }

    // ---- minimal controllable clock ----------------------------------------------

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

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
