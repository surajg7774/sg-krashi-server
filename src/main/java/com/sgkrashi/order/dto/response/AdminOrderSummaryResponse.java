package com.sgkrashi.order.dto.response;

import com.sgkrashi.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderSummaryResponse(
        Long id,
        String orderNumber,
        Long userId,
        String userName,
        String userEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        int itemCount,
        boolean refunded,
        Instant refundedAt,
        Instant createdAt,
        /** True when the order needs an admin's attention (e.g. paid after being marked Payment Failed). */
        boolean needsAttention
) {}
