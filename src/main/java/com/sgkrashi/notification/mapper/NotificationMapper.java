package com.sgkrashi.notification.mapper;

import com.sgkrashi.notification.dto.response.NotificationResponse;
import com.sgkrashi.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getRelatedType(),
                notification.getRelatedId(),
                notification.getCreatedAt()
        );
    }
}
