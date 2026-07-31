package com.sgkrashi.order.dto.request;

import com.sgkrashi.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** {@code adminNotes} is always applied (even when {@code status} is unchanged from the order's current status) — see {@code OrderService#updateOrderStatus}'s Javadoc. */
public record AdminOrderStatusUpdateRequest(
        @NotNull(message = "Status is required")
        OrderStatus status,

        String adminNotes
) {
}
