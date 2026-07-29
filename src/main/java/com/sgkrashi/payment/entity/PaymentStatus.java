package com.sgkrashi.payment.entity;

/** CREATED is the state right after a gateway order is opened; PAID and FAILED are terminal. */
public enum PaymentStatus {
    CREATED,
    PAID,
    FAILED
}
