package com.sgkrashi.notification.event;

/**
 * Shared between Order and Booking payment failures — {@code payableType} is
 * {@code "ORDER"}/{@code "BOOKING"}, matching {@code Payment.payableType}'s
 * existing convention (Module 6), rather than two near-identical event types.
 */
public record PaymentFailedEvent(String payableType, Long payableId, Long userId) {
}
