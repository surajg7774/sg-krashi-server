package com.sgkrashi.notification.dto.response;

import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        boolean read,
        NotificationRelatedType relatedType,
        Long relatedId,
        Instant createdAt
) {
}
