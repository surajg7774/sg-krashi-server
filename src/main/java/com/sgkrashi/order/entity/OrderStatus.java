package com.sgkrashi.order.entity;

/**
 * Lifecycle of an order. PENDING_PAYMENT is the state immediately after checkout
 * (stock already decremented); PAYMENT_FAILED restores that stock. Cancellation
 * beyond a failed payment is out of scope for this module.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED
}
