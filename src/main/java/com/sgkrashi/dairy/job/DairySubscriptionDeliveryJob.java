package com.sgkrashi.dairy.job;

import com.sgkrashi.dairy.config.DairyProperties;
import com.sgkrashi.dairy.config.DairyTime;
import com.sgkrashi.dairy.service.DairyDeliveryPreparationService;
import com.sgkrashi.dairy.service.DairyDeliveryPreparationService.RunSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Prepares tomorrow's subscription deliveries each evening (IST). The evening runs are two so that a run missed because
 * the server was restarting is made up two hours later; the second run only fills gaps, since preparing a date twice is
 * harmless (see {@link DairyDeliveryPreparationService}).
 *
 * <p><b>Does nothing unless {@code dairy.subscriptions.job-enabled} is true</b> (DAIRY_SUBSCRIPTIONS_JOB_ENABLED, default
 * false): no deliveries are prepared, no stock changes, no notifications are sent.
 */
@Component
public class DairySubscriptionDeliveryJob {

    private static final Logger log = LoggerFactory.getLogger(DairySubscriptionDeliveryJob.class);

    private final DairyProperties properties;
    private final DairyTime time;
    private final DairyDeliveryPreparationService preparationService;

    public DairySubscriptionDeliveryJob(DairyProperties properties, DairyTime time, DairyDeliveryPreparationService preparationService) {
        this.properties = properties;
        this.time = time;
        this.preparationService = preparationService;
    }

    @Scheduled(cron = "${dairy.subscriptions.job-cron:0 0 20,22 * * *}", zone = "Asia/Kolkata")
    public void prepareTomorrow() {
        runOnce();
    }

    /** Public so tests (and, if ever wanted, an admin trigger) can run it directly. Returns null when the job is switched off. */
    public RunSummary runOnce() {
        if (!properties.subscriptionJobEnabled()) {
            log.debug("Dairy subscription job skipped: dairy.subscriptions.job-enabled is false");
            return null;
        }
        LocalDate date = time.tomorrow();
        RunSummary summary = preparationService.prepareFor(date);
        log.info("Dairy subscription deliveries for {}: considered={} scheduled={} outOfStock={} alreadyPrepared={} failed={}",
                summary.date(), summary.considered(), summary.scheduled(), summary.outOfStock(), summary.alreadyPrepared(), summary.failed());
        return summary;
    }
}
