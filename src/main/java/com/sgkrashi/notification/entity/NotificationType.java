package com.sgkrashi.notification.entity;

/** What triggered a {@link Notification}. One constant per real, reachable status transition. */
public enum NotificationType {
    ORDER_PLACED,
    ORDER_CONFIRMED,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    PAYMENT_FAILED,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    BOOKING_COMPLETED,
    INQUIRY_STATUS_CHANGED,
    REFUND_PROCESSED,
    WEATHER_ADVISORY,
    PAYOUT_APPROVED,
    PAYOUT_PAID,
    SUBSCRIPTION_CREATED,
    SUBSCRIPTION_DELIVERY_SKIPPED,
    SUBSCRIPTION_DELIVERED,
    SUBSCRIPTION_DELIVERY_FAILED
}
