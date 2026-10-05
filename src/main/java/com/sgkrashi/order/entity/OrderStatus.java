package com.sgkrashi.order.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an order. PENDING_PAYMENT is the state immediately after checkout
 * (stock already decremented); PAYMENT_FAILED restores that stock.
 * SHIPPED is an optional, admin-set step between CONFIRMED and DELIVERED — this
 * platform has no shipping/carrier integration, so it only ever means "an admin
 * says it has been dispatched"; an order may skip it.
 * DELIVERED is set manually by an Admin via the plain status-update endpoint,
 * from CONFIRMED or SHIPPED (unlike {@code BookingStatus.COMPLETED}, which has
 * its {@code end_date} to key off of).
 * REFUNDED (Module 16) is reached only via {@code RefundService} after a real
 * gateway refund succeeds — never settable directly through the plain admin
 * status-update endpoint.
 *
 * <p>Which moves are legal is decided here, in one place — see
 * {@link #canTransitionTo}. {@code OrderServiceImpl.applyTransition} enforces it
 * for every path (customer, admin, payment webhook, refund).
 *
 * <p>Stored via {@code @Enumerated(EnumType.STRING)} on a {@code VARCHAR(30)}
 * column — adding a constant requires no schema migration.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    PAYMENT_FAILED,
    REFUNDED;

    private static final Set<OrderStatus> FROM_PENDING_PAYMENT = EnumSet.of(CONFIRMED, PAYMENT_FAILED);
    private static final Set<OrderStatus> FROM_CONFIRMED = EnumSet.of(SHIPPED, DELIVERED, REFUNDED);
    private static final Set<OrderStatus> FROM_SHIPPED = EnumSet.of(DELIVERED, REFUNDED);
    private static final Set<OrderStatus> FROM_DELIVERED = EnumSet.of(REFUNDED);
    /**
     * PAYMENT_FAILED -> REFUNDED is deliberately legal even though PAYMENT_FAILED
     * is otherwise terminal: it is only ever reachable through
     * {@code RefundService}, which needs a PAID payment. The one way an order is
     * PAYMENT_FAILED while its payment is PAID is a payment captured after an
     * admin had already marked the order failed — the customer's money must still
     * be refundable. The admin status endpoint never accepts REFUNDED.
     */
    private static final Set<OrderStatus> FROM_PAYMENT_FAILED = EnumSet.of(REFUNDED);

    public boolean canTransitionTo(OrderStatus target) {
        Set<OrderStatus> allowed = switch (this) {
            case PENDING_PAYMENT -> FROM_PENDING_PAYMENT;
            case CONFIRMED -> FROM_CONFIRMED;
            case SHIPPED -> FROM_SHIPPED;
            case DELIVERED -> FROM_DELIVERED;
            case PAYMENT_FAILED -> FROM_PAYMENT_FAILED;
            case REFUNDED -> Set.of();
        };
        return allowed.contains(target);
    }

    /** Customer-facing wording, used in error messages. */
    public String label() {
        return switch (this) {
            case PENDING_PAYMENT -> "Pending Payment";
            case CONFIRMED -> "Confirmed";
            case SHIPPED -> "Shipped";
            case DELIVERED -> "Delivered";
            case PAYMENT_FAILED -> "Payment Failed";
            case REFUNDED -> "Refunded";
        };
    }
}
