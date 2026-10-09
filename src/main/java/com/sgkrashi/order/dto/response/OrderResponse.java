package com.sgkrashi.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sgkrashi.dairy.dto.DairyDtos.OrderDeliveryResponse;
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
        Instant createdAt,
        /** The slot and date chosen for dairy; left out of the JSON for orders without dairy. */
        @JsonInclude(JsonInclude.Include.NON_NULL) OrderDeliveryResponse delivery
) {}
