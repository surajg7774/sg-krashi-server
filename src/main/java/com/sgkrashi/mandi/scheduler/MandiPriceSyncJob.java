package com.sgkrashi.mandi.scheduler;

import com.sgkrashi.mandi.client.MandiApiNotConfiguredException;
import com.sgkrashi.mandi.client.MandiApiUnavailableException;
import com.sgkrashi.mandi.client.MandiPriceApiClient;
import com.sgkrashi.mandi.client.MandiPriceRow;
import com.sgkrashi.mandi.entity.MandiPrice;
import com.sgkrashi.mandi.repository.MandiPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Component
public class MandiPriceSyncJob {

    private static final Logger log = LoggerFactory.getLogger(MandiPriceSyncJob.class);

    // Matches SG Krashi's actual crop-listing categories (wheat/maize/gram
    // under "grains"/"pulses", onion/potato/tomato under "vegetables") using
    // Agmarknet's own commodity-name spelling, MP-relevant crops first.
    private static final List<String> PRIORITY_COMMODITIES = List.of(
            "Wheat", "Soyabean", "Gram", "Maize", "Onion", "Potato", "Tomato");
    private static final String PRIORITY_STATE = "Madhya Pradesh";

    private final MandiPriceApiClient mandiPriceApiClient;
    private final MandiPriceRepository mandiPriceRepository;

    public MandiPriceSyncJob(MandiPriceApiClient mandiPriceApiClient, MandiPriceRepository mandiPriceRepository) {
        this.mandiPriceApiClient = mandiPriceApiClient;
        this.mandiPriceRepository = mandiPriceRepository;
    }

    /** Runs daily at 6:30 AM IST — after Agmarknet's own daily publish, well clear of business hours. */
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Kolkata")
    public void syncDaily() {
        runOnce();
    }

    /**
     * @return how many rows were upserted this run (0 if the API key isn't configured yet, or the call failed — never throws)
     */
    public int runOnce() {
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
            log.info("MandiPriceSyncJob ran: upserted {} row(s)", upserted);
            return upserted;
        } catch (MandiApiNotConfiguredException ex) {
            log.info("MandiPriceSyncJob skipped: {}", ex.getMessage());
            return upserted;
        } catch (MandiApiUnavailableException ex) {
            log.warn("MandiPriceSyncJob failed partway through, keeping already-upserted and previously cached data: {}", ex.getMessage());
            return upserted;
        }
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
