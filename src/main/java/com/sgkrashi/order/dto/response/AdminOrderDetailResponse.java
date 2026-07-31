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
        List<OrderStatusEventResponse> statusHistory,
        String adminNotes,
        boolean refunded,
        Instant refundedAt,
        Instant createdAt
) {}
