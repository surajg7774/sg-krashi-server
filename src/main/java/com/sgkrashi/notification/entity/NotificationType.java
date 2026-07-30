package com.sgkrashi.notification.entity;

/** What triggered a {@link Notification}. One constant per real, reachable status transition — see this module's own scope note on not inventing hooks for unreachable states (e.g. no ORDER_DELIVERED, no BOOKING_COMPLETED). */
public enum NotificationType {
    ORDER_CONFIRMED,
    PAYMENT_FAILED,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    INQUIRY_STATUS_CHANGED
}
