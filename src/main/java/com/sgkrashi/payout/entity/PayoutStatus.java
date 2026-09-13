package com.sgkrashi.payout.entity;

/**
 * Lifecycle of a {@link FarmerPayout} batch. There is no PENDING state here —
 * "pending/accrued" (earned but not yet swept into a batch) is a virtual
 * figure computed on the fly from unbatched {@code order_items}, never a row
 * in this table (see {@code PayoutService.getPendingAccrued}).
 *
 * <p>BATCHED means the weekly job has aggregated it but an Admin has not yet
 * approved it — this is also the only status a payout can still receive new
 * lines under (a fresh sweep, or a refund clawback), so it's the "still open"
 * state. APPROVED means an Admin signed off but the manual bank transfer
 * hasn't been confirmed yet. PAID means the transfer was confirmed — this
 * platform does not integrate a real payout gateway, so PAID is asserted by
 * an Admin after transferring funds outside the app.
 */
public enum PayoutStatus {
    BATCHED,
    APPROVED,
    PAID
}
