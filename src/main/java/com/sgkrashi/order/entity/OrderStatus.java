package com.sgkrashi.order.entity;

/**
 * Lifecycle of an order. PENDING_PAYMENT is the state immediately after checkout
 * (stock already decremented); PAYMENT_FAILED restores that stock.
 * REFUNDED (Module 16) is reached only via {@code RefundService} after a real
 * gateway refund succeeds — never settable directly through the plain admin
 * status-update endpoint, which only exposes CONFIRMED/PAYMENT_FAILED.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED,
    REFUNDED
}
