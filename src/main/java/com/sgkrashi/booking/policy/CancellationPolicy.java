package com.sgkrashi.booking.policy;

import com.sgkrashi.booking.entity.BookableType;

import java.time.Duration;
import java.util.Map;

/**
 * Free-cancellation window, configurable per {@link BookableType} — added in
 * Module 9 because Equipment's 48-hour window (Module 8) was originally a
 * hardcoded constant in {@code BookingServiceImpl}, and Farm Stay needs a
 * stricter one (the architecture doc's own stated intent). A lookup keyed by
 * the generic {@code BookableType} enum, not an if/else chain, so a
 * hypothetical third bookable type only ever needs one new map entry here —
 * never another edit to {@code BookingServiceImpl}'s cancellation logic
 * itself.
 */
public record CancellationPolicy(Duration freeWindow, String windowLabel) {

    private static final Map<BookableType, CancellationPolicy> POLICIES = Map.of(
            BookableType.EQUIPMENT, new CancellationPolicy(Duration.ofHours(48), "48 hours"),
            BookableType.STAY, new CancellationPolicy(Duration.ofDays(7), "7 days")
    );

    public static CancellationPolicy forType(BookableType bookableType) {
        CancellationPolicy policy = POLICIES.get(bookableType);
        if (policy == null) {
            throw new IllegalStateException("No cancellation policy configured for " + bookableType);
        }
        return policy;
    }
}
