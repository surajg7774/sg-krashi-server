package com.sgkrashi.order.dto.response;

import com.sgkrashi.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        String shippingLine1,
        String shippingLine2,
        String shippingCity,
        String shippingState,
        String shippingPincode,
        List<OrderItemResponse> items,
        List<OrderStatusEventResponse> statusHistory,
        Instant createdAt
) {}
