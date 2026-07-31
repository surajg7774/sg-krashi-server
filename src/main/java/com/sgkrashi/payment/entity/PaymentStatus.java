package com.sgkrashi.payment.entity;

/**
 * CREATED is the state right after a gateway order is opened; PAID, FAILED and
 * REFUNDED are terminal. REFUNDED (Module 16) is only ever reached from PAID,
 * via {@code RefundService} after a real Razorpay refund call succeeds.
 */
public enum PaymentStatus {
    CREATED,
    PAID,
    FAILED,
    REFUNDED
}
