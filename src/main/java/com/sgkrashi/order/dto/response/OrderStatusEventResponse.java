package com.sgkrashi.order.dto.response;

import com.sgkrashi.order.entity.OrderStatus;

import java.time.Instant;

public record OrderStatusEventResponse(OrderStatus status, String note, Instant occurredAt) {}
