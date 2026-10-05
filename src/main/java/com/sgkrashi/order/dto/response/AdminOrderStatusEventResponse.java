package com.sgkrashi.order.dto.response;

import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.entity.StatusChangeActor;

import java.time.Instant;

/**
 * {@link OrderStatusEventResponse} plus who made the change — admin screens only; customers never see
 * the actor. {@code changedByRole} is null for rows older than V40 (never recorded). {@code changedByName}
 * is set only for an ADMIN actor.
 */
public record AdminOrderStatusEventResponse(
        OrderStatus status,
        String note,
        Instant occurredAt,
        StatusChangeActor.Role changedByRole,
        Long changedByUserId,
        String changedByName
) {}
