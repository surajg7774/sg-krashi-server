package com.sgkrashi.payout.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.payout.dto.response.AdminPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.AdminPayoutSummaryResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutSummaryResponse;
import com.sgkrashi.payout.dto.response.PendingPayoutSummaryResponse;
import com.sgkrashi.payout.entity.PayoutStatus;

import java.util.List;

public interface PayoutService {

    /** Which farmers currently have at least one unbatched DELIVERED item — what {@code PayoutBatchJob} needs to know what to loop over. */
    List<Long> findFarmerIdsPendingSweep();

    /**
     * Aggregates one farmer's currently-unbatched DELIVERED crop-listing
     * order items into (or onto their existing open) BATCHED payout, at a
     * flat 5% platform commission. Idempotent — see {@code
     * OrderItemRepository.findUnbatchedDeliveredItemsForFarmer}'s Javadoc.
     * Each call is its own transaction (see the {@code @Transactional} on
     * the implementation) so one farmer's failure during a batch run can't
     * roll back another farmer's already-committed sweep — same reasoning
     * as {@code BookingService#markCompleted} being called per-booking from
     * {@code BookingCompletionJob}'s loop rather than one big transaction.
     *
     * @return how many items were swept for this farmer (0 if none were pending)
     */
    int sweepFarmer(Long farmerId);

    /**
     * Reacts to an Order's refund. If the order's crop-listing item(s) were
     * never linked into a payout, this is a no-op — they simply stay
     * excluded from all future sweeps. If an item was already linked (in
     * any payout status), the original line is left untouched and a new
     * negative CLAWBACK line is added to the farmer's current open batch
     * (creating one if none exists) instead.
     */
    void handleOrderRefunded(Long orderId);

    PaginatedResponse<FarmerPayoutSummaryResponse> listOwnPayouts(Long farmerId, int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if the payout doesn't exist OR belongs to a different farmer. */
    FarmerPayoutDetailResponse getOwnPayoutDetail(Long farmerId, Long payoutId);

    /** Live, unpersisted preview of what the next batch run would pay this farmer — see {@code PendingPayoutSummaryResponse}'s Javadoc. */
    PendingPayoutSummaryResponse getPendingAccrued(Long farmerId);

    PaginatedResponse<AdminPayoutSummaryResponse> listForAdmin(PayoutStatus status, int page, int size);

    AdminPayoutDetailResponse getDetailForAdmin(Long payoutId);

    /**
     * BATCHED -> APPROVED. Idempotent if already APPROVED.
     *
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the payout is not currently BATCHED or APPROVED
     */
    AdminPayoutDetailResponse approve(Long payoutId, Long adminId);

    /**
     * APPROVED -> PAID, asserted by an Admin after transferring funds
     * manually outside the app — no payout gateway is integrated in this
     * pass. Idempotent if already PAID.
     *
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the payout is not currently APPROVED or PAID
     */
    AdminPayoutDetailResponse markPaid(Long payoutId);
}
