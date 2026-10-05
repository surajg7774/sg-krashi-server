package com.sgkrashi.order.entity;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.sgkrashi.order.entity.OrderStatus.CONFIRMED;
import static com.sgkrashi.order.entity.OrderStatus.DELIVERED;
import static com.sgkrashi.order.entity.OrderStatus.PAYMENT_FAILED;
import static com.sgkrashi.order.entity.OrderStatus.PENDING_PAYMENT;
import static com.sgkrashi.order.entity.OrderStatus.REFUNDED;
import static com.sgkrashi.order.entity.OrderStatus.SHIPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The approved lifecycle, pinned as a full matrix so adding a status or edge forces a deliberate test change. */
class OrderStatusTransitionTest {

    private static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = new EnumMap<>(OrderStatus.class);

    static {
        EXPECTED.put(PENDING_PAYMENT, EnumSet.of(CONFIRMED, PAYMENT_FAILED));
        EXPECTED.put(CONFIRMED, EnumSet.of(SHIPPED, DELIVERED, REFUNDED));
        EXPECTED.put(SHIPPED, EnumSet.of(DELIVERED, REFUNDED));
        EXPECTED.put(DELIVERED, EnumSet.of(REFUNDED));
        // Only reachable through RefundService (see OrderStatus): a payment captured after the order was marked failed.
        EXPECTED.put(PAYMENT_FAILED, EnumSet.of(REFUNDED));
        EXPECTED.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    @Test
    void everyStatusPairMatchesTheApprovedTable() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertEquals(EXPECTED.get(from).contains(to), from.canTransitionTo(to),
                        from + " -> " + to);
            }
        }
    }

    @Test
    void theTableCoversEveryStatus() {
        assertEquals(EnumSet.allOf(OrderStatus.class), EXPECTED.keySet());
    }

    @Test
    void shippedIsOptionalButNothingGoesBackwards() {
        assertTrue(CONFIRMED.canTransitionTo(DELIVERED), "an order may skip SHIPPED");
        assertFalse(DELIVERED.canTransitionTo(CONFIRMED));
        assertFalse(DELIVERED.canTransitionTo(SHIPPED));
        assertFalse(SHIPPED.canTransitionTo(CONFIRMED));
        assertFalse(REFUNDED.canTransitionTo(CONFIRMED));
        assertFalse(PAYMENT_FAILED.canTransitionTo(CONFIRMED), "a late capture must not resurrect a failed order");
    }

    @Test
    void labelsAreCustomerFacing() {
        assertEquals("Pending Payment", PENDING_PAYMENT.label());
        assertEquals("Payment Failed", PAYMENT_FAILED.label());
        assertEquals("Shipped", SHIPPED.label());
    }
}
