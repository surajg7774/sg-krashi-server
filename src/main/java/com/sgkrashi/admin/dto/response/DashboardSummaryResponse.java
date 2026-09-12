package com.sgkrashi.admin.dto.response;

import java.math.BigDecimal;

/**
 * {@link OrdersSummary#todayConfirmed} counts CONFIRMED and DELIVERED
 * together — both mean "successfully paid for today", DELIVERED being just a
 * later point in that same successful order's life. {@link
 * BookingsSummary#completed} is the real {@code BookingStatus.COMPLETED}
 * count, set by {@code BookingCompletionJob} — no longer a same-day proxy.
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

    public record BookingsSummary(long upcomingConfirmed, long pendingPayment, long completed) {
    }

    public record RevenueSummary(BigDecimal today, BigDecimal thisWeek, BigDecimal thisMonth) {
    }
}
