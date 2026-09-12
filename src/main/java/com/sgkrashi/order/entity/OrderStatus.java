package com.sgkrashi.order.entity;

/**
 * Lifecycle of an order. PENDING_PAYMENT is the state immediately after checkout
 * (stock already decremented); PAYMENT_FAILED restores that stock.
 * DELIVERED is reachable only from CONFIRMED, set manually by an Admin via the
 * plain status-update endpoint — this platform has no real shipping/carrier
 * integration, so there is no automatic trigger for it (unlike {@code
 * BookingStatus.COMPLETED}, which has its {@code end_date} to key off of).
 * REFUNDED (Module 16) is reached only via {@code RefundService} after a real
 * gateway refund succeeds — never settable directly through the plain admin
 * status-update endpoint, which only exposes CONFIRMED/DELIVERED/PAYMENT_FAILED.
 *
 * <p>Stored via {@code @Enumerated(EnumType.STRING)} on a {@code VARCHAR(30)}
 * column — adding this constant required no schema migration.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    DELIVERED,
    PAYMENT_FAILED,
    REFUNDED
}
