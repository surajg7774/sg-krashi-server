package com.sgkrashi.order.dto.response;

import com.sgkrashi.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderDetailResponse(
        Long id,
        String orderNumber,
        Long userId,
        String userName,
        String userEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        String shippingLine1,
        String shippingLine2,
        String shippingCity,
        String shippingState,
        String shippingPincode,
        List<OrderItemResponse> items,
        List<AdminOrderStatusEventResponse> statusHistory,
        String adminNotes,
        boolean refunded,
        Instant refundedAt,
        Instant createdAt,
        /** Gateway payment status (CREATED/PAID/FAILED/REFUNDED), null if no payment was ever started. */
        String paymentStatus,
        /** Non-null when the order needs an admin's attention — see {@code OrderServiceImpl#attentionMessage}. */
        String attentionMessage
) {}
