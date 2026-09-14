package com.sgkrashi.notification.entity;

/** What triggered a {@link Notification}. One constant per real, reachable status transition. */
public enum NotificationType {
    ORDER_CONFIRMED,
    ORDER_DELIVERED,
    PAYMENT_FAILED,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    BOOKING_COMPLETED,
    INQUIRY_STATUS_CHANGED,
    REFUND_PROCESSED,
    WEATHER_ADVISORY
}
