package com.sgkrashi.booking.entity;

/**
 * Mirrors {@code OrderStatus}'s shape deliberately, for consistency across the
 * platform. Note there is no distinct "payment failed" state: a booking whose
 * payment fails is CANCELLED outright (see {@code BookingServiceImpl}) rather
 * than left in a limbo state, since — unlike an Order — there's no separate
 * "restore stock" step to perform; cancelling immediately frees the calendar
 * slot for other customers.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
