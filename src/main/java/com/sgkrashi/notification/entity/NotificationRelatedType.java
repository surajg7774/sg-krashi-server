package com.sgkrashi.notification.entity;

/** What {@link Notification#getRelatedId()} points to — no FK, same polymorphic-lite tradeoff as MediaAsset/Review. */
public enum NotificationRelatedType {
    ORDER,
    BOOKING,
    INQUIRY
}
