package com.sgkrashi.mandi.scheduler;

import com.sgkrashi.mandi.client.MandiApiNotConfiguredException;
import com.sgkrashi.mandi.client.MandiApiUnavailableException;
import com.sgkrashi.mandi.client.MandiPriceApiClient;
import com.sgkrashi.mandi.client.MandiPriceRow;
import com.sgkrashi.mandi.entity.MandiPrice;
import com.sgkrashi.mandi.repository.MandiPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Daily sync from data.gov.in's Agmarknet mandi-price dataset into the local
 * {@code mandi_prices} cache — Agmarknet itself publishes once a day, so
 * this runs once a day too (6:30 AM IST, after that daily publish) rather
 * than polling. Same shape as {@code BookingCompletionJob}/{@code
 * PayoutBatchJob}: the {@code @Scheduled} method is a one-line trampoline to
 * a public {@code runOnce()} an admin can also trigger on demand.
 *
 * <p>Fetches broadly (no hard state restriction — the stored table and
 * public API support any state/commodity), but iterates a Madhya-Pradesh
 * -weighted, SG-Krashi-relevant commodity priority list first, so if the
 * free API tier's rate/row limits constrain how much can be pulled in one
 * run (unconfirmed until a real key exists), the most relevant data lands
 * first rather than being cut off by an alphabetically-arbitrary API order.
 *
 * <p><b>Catch-up attempts.</b> data.gov.in has had multi-day outages, and a
 * once-a-day schedule would then add up to a day of lag after it recovers. So
 * two extra attempts (12:30 and 18:30 IST) exist — but each runs <i>only</i>
 * if the previous attempt failed or no row has changed in the last 24 hours,
 * and does nothing otherwise, so a healthy day is still exactly one call
 * series to data.gov.in. The retry/backoff inside {@code MandiPriceApiClient}
 * is unchanged.
 *
 * <p><b>Logging.</b> One WARN per failed attempt and one INFO when the source
 * recovers (see {@link MandiSyncMessages}) — the client's own per-call lines
 * are unchanged.
 */
@Component
public class MandiPriceSyncJob {

    private static final Logger log = LoggerFactory.getLogger(MandiPriceSyncJob.class);

    static final String SCHEDULE_ZONE = "Asia/Kolkata";
    static final String DAILY_CRON = "0 30 6 * * *";
    static final String CATCH_UP_CRON = "0 30 12,18 * * *";
    /** A table with no row changed inside this window counts as having no fresh data. */
    static final Duration FRESHNESS_WINDOW = Duration.ofHours(24);
    /** Older than this (or never) and a success is reported as the source having recovered. */
    static final Duration STALE_AFTER = Duration.ofHours(48);

    // Matches SG Krashi's actual crop-listing categories (wheat/maize/gram
    // under "grains"/"pulses", onion/potato/tomato under "vegetables") using
    // Agmarknet's own commodity-name spelling, MP-relevant crops first.
    private static final List<String> PRIORITY_COMMODITIES = List.of(
            "Wheat", "Soyabean", "Gram", "Maize", "Onion", "Potato", "Tomato");
    private static final String PRIORITY_STATE = "Madhya Pradesh";

    private final MandiPriceApiClient mandiPriceApiClient;
    private final MandiPriceRepository mandiPriceRepository;
    private final Clock clock;
    private final MandiSyncTracker tracker = new MandiSyncTracker();

    @Autowired
    public MandiPriceSyncJob(MandiPriceApiClient mandiPriceApiClient, MandiPriceRepository mandiPriceRepository) {
        this(mandiPriceApiClient, mandiPriceRepository, Clock.systemUTC());
    }

    MandiPriceSyncJob(MandiPriceApiClient mandiPriceApiClient, MandiPriceRepository mandiPriceRepository, Clock clock) {
        this.mandiPriceApiClient = mandiPriceApiClient;
        this.mandiPriceRepository = mandiPriceRepository;
        this.clock = clock;
    }

    /** Runs daily at 6:30 AM IST — after Agmarknet's own daily publish, well clear of business hours. Always attempts. */
    @Scheduled(cron = DAILY_CRON, zone = SCHEDULE_ZONE)
    public void syncDaily() {
        attempt("scheduled-0630-IST");
    }

    /** 12:30 and 18:30 IST — attempts only when the previous attempt failed or the data is stale; otherwise a no-op. */
    @Scheduled(cron = CATCH_UP_CRON, zone = SCHEDULE_ZONE)
    public void syncCatchUp() {
        if (!shouldRunCatchUp()) {
            log.debug("Mandi catch-up sync skipped: previous attempt succeeded and data is fresh");
            return;
        }
        attempt("catch-up");
    }

    /**
     * @return how many rows were upserted this run (0 if the API key isn't configured yet, or the call failed — never throws)
     */
    public int runOnce() {
        return attempt("manual");
    }

    boolean shouldRunCatchUp() {
        if (!mandiPriceApiClient.isConfigured()) {
            return false; // nothing to retry — the 6:30 run already logs that the key is missing
        }
        if (tracker.previousAttemptFailed()) {
            return true;
        }
        Instant newest = newestSuccess();
        return newest == null || Duration.between(newest, clock.instant()).compareTo(FRESHNESS_WINDOW) >= 0;
    }

    // synchronized: the scheduler thread and an admin's manual run must not
    // pull from data.gov.in at the same time.
    private synchronized int attempt(String trigger) {
        // Read before upserting — the upserts themselves move the table's "last updated".
        Instant previousSuccess = newestSuccess();
        int upserted = 0;
        try {
            // Madhya-Pradesh-first pass, one commodity at a time so an API
            // key with a low per-call row cap still lands the most relevant
            // rows before any budget/rate limit is hit.
            for (String commodity : PRIORITY_COMMODITIES) {
                upserted += fetchAndUpsert(PRIORITY_STATE, commodity);
            }
            // ...then one broad, unfiltered pass to pick up everything else
            // this run's page budget allows.
            upserted += fetchAndUpsert(null, null);
            onSuccess(trigger, upserted, previousSuccess);
            return upserted;
        } catch (MandiApiNotConfiguredException ex) {
            log.info("MandiPriceSyncJob skipped: {}", ex.getMessage());
            return upserted;
        } catch (MandiApiUnavailableException ex) {
            onFailure(trigger, ex, upserted, previousSuccess);
            return upserted;
        }
    }

    private void onSuccess(String trigger, int upserted, Instant previousSuccess) {
        Instant now = clock.instant();
        Optional<MandiSyncTracker.Recovery> outage = tracker.recordSuccess(now);
        boolean dataWasStale = previousSuccess == null || Duration.between(previousSuccess, now).compareTo(STALE_AFTER) >= 0;
        if (outage.isPresent() || dataWasStale) {
            // Second condition covers a restart between the failure and the
            // recovery (the tracker starts empty): the table still shows the gap.
            log.info(MandiSyncMessages.recovered(mandiPriceApiClient.getHost(), trigger, upserted, previousSuccess, now,
                    outage.map(MandiSyncTracker.Recovery::failedAttempts).orElse(0)));
        } else {
            log.info("MandiPriceSyncJob ran: upserted {} row(s)", upserted);
        }
    }

    private void onFailure(String trigger, MandiApiUnavailableException ex, int upsertedBeforeFailure, Instant previousSuccess) {
        Instant now = clock.instant();
        int consecutiveFailures = tracker.recordFailure(now);
        log.warn(MandiSyncMessages.failed(mandiPriceApiClient.getHost(), MandiSyncFailure.describe(ex), trigger,
                previousSuccess, now, consecutiveFailures, upsertedBeforeFailure));
    }

    /** Latest of: this process's last successful attempt, and the table's most recent row change. Null if neither is known. */
    private Instant newestSuccess() {
        Instant fromMemory = tracker.lastSuccessAt();
        Instant fromTable = null;
        try {
            fromTable = mandiPriceRepository.findLastSyncedAt().orElse(null);
        } catch (RuntimeException ex) {
            // A database hiccup must not break the sync or its logging; "unknown" is handled as stale.
            log.debug("Could not read the mandi table's last update time: {}", ex.getClass().getSimpleName());
        }
        if (fromMemory == null) {
            return fromTable;
        }
        if (fromTable == null) {
            return fromMemory;
        }
        return fromMemory.isAfter(fromTable) ? fromMemory : fromTable;
    }

    @Transactional
    protected int fetchAndUpsert(String stateFilter, String commodityFilter) {
        List<MandiPriceRow> rows = mandiPriceApiClient.fetchAll(stateFilter, commodityFilter);
        int upserted = 0;
        for (MandiPriceRow row : rows) {
            upsert(row);
            upserted++;
        }
        return upserted;
    }

    private void upsert(MandiPriceRow row) {
        Optional<MandiPrice> existing = mandiPriceRepository.findByCommodityAndMarketNameAndPriceDate(
                row.commodity(), row.marketName(), row.priceDate());
        MandiPrice price = existing.orElseGet(MandiPrice::new);
        price.setCommodity(row.commodity());
        price.setMarketName(row.marketName());
        price.setState(row.state());
        price.setDistrict(row.district());
        price.setMinPrice(row.minPrice());
        price.setMaxPrice(row.maxPrice());
        price.setModalPrice(row.modalPrice());
        price.setPriceDate(row.priceDate());
        mandiPriceRepository.save(price);
    }
}
