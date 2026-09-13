package com.sgkrashi.payout.dto.response;

import java.math.BigDecimal;

/**
 * The farmer dashboard's "pending (accrued, not yet batched)" figure — a
 * live preview computed from currently-unbatched DELIVERED order items,
 * never a database row. Uses the exact same per-line 5% split the weekly
 * job will actually apply, so this figure is never a promise the real
 * batch can't reproduce.
 */
public record PendingPayoutSummaryResponse(
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        int itemCount
) {
}
