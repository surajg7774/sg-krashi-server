package com.sgkrashi.notification.event;

import java.math.BigDecimal;

/**
 * Shared between Order and Booking refunds — {@code payableType} is
 * {@code "ORDER"}/{@code "BOOKING"}, same convention as {@link PaymentFailedEvent}.
 * Published by {@code OrderService#markRefunded}/{@code BookingService#markRefunded}
 * themselves (mirroring how those methods' CONFIRMED/CANCELLED siblings already
 * self-publish their own events) — {@code amount} is the order's/booking's own
 * total, which always equals the refunded {@code Payment}'s amount since this
 * module only ever does full refunds.
 */
public record RefundProcessedEvent(String payableType, Long payableId, Long userId, BigDecimal amount) {
}
