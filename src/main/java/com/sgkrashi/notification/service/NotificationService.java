package com.sgkrashi.notification.service;

import com.sgkrashi.notification.dto.response.NotificationListResponse;
import com.sgkrashi.notification.dto.response.NotificationResponse;
import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;

public interface NotificationService {

    /**
     * Persists the notification (the "in-app" channel — always happens) then
     * dispatches to every registered {@code NotificationSender} (email today).
     * A sender failure is caught and logged per-sender — it must never undo
     * the persisted row or block other senders. Called only from {@code
     * NotificationEventListener}'s {@code AFTER_COMMIT} handlers, never from
     * inside the transaction that changed the Order/Booking/Inquiry.
     */
    void notify(Long userId, NotificationType type, String title, String message, NotificationRelatedType relatedType, Long relatedId);

    NotificationListResponse getMyNotifications(int page, int size);

    NotificationResponse markAsRead(Long notificationId);

    void markAllAsRead();
}
