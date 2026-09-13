package com.sgkrashi.payout.scheduler;

import com.sgkrashi.payout.service.PayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Weekly Farmer Payout batch — same shape as {@code BookingCompletionJob}:
 * runs on a schedule, splits the actual work out into a separately
 * triggerable {@link #runOnce()}, and calls one per-unit business method
 * ({@link PayoutService#sweepFarmer}) per farmer rather than wrapping the
 * whole run in one transaction, so one farmer's failure can't roll back
 * another's already-committed sweep.
 *
 * <p>Idempotent by construction: {@link PayoutService#findFarmerIdsPendingSweep}
 * only ever returns farmers with a currently-unbatched DELIVERED item, and
 * {@code farmer_payout_lines}' {@code UNIQUE(order_item_id, line_type)}
 * constraint backs that up at the database level — running this job twice
 * in a row, or re-running it after a partial failure, is always safe.
 */
@Component
public class PayoutBatchJob {

    private static final Logger log = LoggerFactory.getLogger(PayoutBatchJob.class);
    private static final ZoneId PAYOUT_ZONE = ZoneId.of("Asia/Kolkata");

    private final PayoutService payoutService;

    public PayoutBatchJob(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    /** Runs weekly, Monday 2:00 AM IST — after {@code BookingCompletionJob}'s daily 1 AM run, well clear of business hours. */
    @Scheduled(cron = "0 0 2 * * MON", zone = "Asia/Kolkata")
    public void runWeeklyBatch() {
        runOnce();
    }

    /**
     * Split out from the {@code @Scheduled} method so this can be triggered
     * on demand (e.g. {@code AdminPayoutController}'s manual-run endpoint,
     * used to verify this job without waiting for its real weekly schedule).
     *
     * @return how many farmers had at least one item swept this run
     */
    public int runOnce() {
        LocalDate today = LocalDate.now(PAYOUT_ZONE);
        List<Long> farmerIds = payoutService.findFarmerIdsPendingSweep();

        if (farmerIds.isEmpty()) {
            log.info("PayoutBatchJob ran at {}: no unbatched DELIVERED crop-listing items for any farmer, nothing to do", today);
            return 0;
        }

        int processed = 0;
        for (Long farmerId : farmerIds) {
            if (payoutService.sweepFarmer(farmerId) > 0) {
                processed++;
            }
        }
        log.info("PayoutBatchJob ran at {}: swept items into payouts for {} farmer(s) (farmer ids: {})", today, processed, farmerIds);
        return processed;
    }
}
