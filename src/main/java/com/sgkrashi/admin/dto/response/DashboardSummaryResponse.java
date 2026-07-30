package com.sgkrashi.admin.dto.response;

import java.math.BigDecimal;

/**
 * Deliberately excludes any "delivered"/"completed" order breakdown —
 * {@code OrderStatus} has no DELIVERED state and {@code BookingStatus
 * .COMPLETED} is never set by any code path (see Module 12's {@code
 * ReviewEligibilityServiceImpl} Javadoc). {@link BookingsSummary
 * #completedByProxy} reuses that same module's "CONFIRMED + endDate passed"
 * workaround rather than inventing a fresh one.
 */
public record DashboardSummaryResponse(
        OrdersSummary orders,
        BookingsSummary bookings,
        long newInquiriesCount,
        long lowStockProductCount,
        RevenueSummary revenue
) {
    public record OrdersSummary(long todayConfirmed, long todayPaymentFailed, long todayPendingPayment) {
    }

    public record BookingsSummary(long upcomingConfirmed, long pendingPayment, long completedByProxy) {
    }

    public record RevenueSummary(BigDecimal today, BigDecimal thisWeek, BigDecimal thisMonth) {
    }
}
