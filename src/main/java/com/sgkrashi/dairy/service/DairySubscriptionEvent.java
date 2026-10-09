package com.sgkrashi.dairy.service;

import java.time.LocalDate;

/** Something a subscription customer should hear about. Published inside a transaction, delivered after it commits. */
public record DairySubscriptionEvent(Kind kind, Long userId, Long subscriptionId, String productName, LocalDate date, String detail) {

    public enum Kind {
        CREATED, DELIVERY_SKIPPED_OUT_OF_STOCK, DELIVERED, DELIVERY_FAILED
    }
}
