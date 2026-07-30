package com.sgkrashi.notification.event;

/** {@code reason} is nullable — customer-initiated cancellations may include one; the payment-failure path always supplies one. */
public record BookingCancelledEvent(Long bookingId, Long userId, String reason) {
}
