package com.sgkrashi.farmer.dto.response;

/**
 * Deliberately excludes revenue/payout figures — Year 2 owns payouts and KYC
 * (architecture doc, Section 4); showing a Farmer a money figure before any
 * payout mechanism exists would misleadingly imply funds owed. {@code
 * ordersContainingListings}/{@code unitsSold} are informational counts only.
 *
 * <p>Also excludes an inquiry count: unlike {@code OrderItem}, {@code
 * Inquiry} has no {@code crop_listing_id} (or any listing-level) FK at all —
 * it only carries a {@code module_type}, not a reference to a specific
 * listing — so there is no clean (or even awkward) path back to {@code
 * farmer_id} for inquiries. That's a genuine schema gap, not a query-cost
 * tradeoff; fixing it would mean adding a nullable FK to {@code inquiries},
 * which is out of this foundation module's scope.
 */
public record FarmerDashboardSummaryResponse(
        long totalListings,
        long activeListings,
        long ordersContainingListings,
        long unitsSold
) {
}
