package com.sgkrashi.dairy.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dairy settings. Every value has a safe default so the app boots with none of them set (a missing required setting
 * once crashed the whole backend on deploy; nothing here may do that).
 *
 * <p>Both subscription switches default to OFF: with {@code dairy.subscriptions.enabled=false} customers cannot create
 * subscriptions (the web hides the screens), and with {@code dairy.subscriptions.job-enabled=false} the nightly job
 * does nothing at all: no deliveries are prepared, no stock changes and no notifications are sent.
 * Environment names: DAIRY_SUBSCRIPTIONS_ENABLED, DAIRY_SUBSCRIPTIONS_JOB_ENABLED.
 */
@Component
public class DairyProperties {

    private final boolean subscriptionsEnabled;
    private final boolean subscriptionJobEnabled;
    private final int minLeadDays;
    private final int bookingWindowDays;
    private final int maxQuantity;

    @Autowired
    public DairyProperties(
            @Value("${dairy.subscriptions.enabled:false}") boolean subscriptionsEnabled,
            @Value("${dairy.subscriptions.job-enabled:false}") boolean subscriptionJobEnabled,
            @Value("${dairy.delivery.min-lead-days:1}") int minLeadDays,
            @Value("${dairy.delivery.window-days:7}") int bookingWindowDays,
            @Value("${dairy.subscriptions.max-quantity:20}") int maxQuantity
    ) {
        this.subscriptionsEnabled = subscriptionsEnabled;
        this.subscriptionJobEnabled = subscriptionJobEnabled;
        this.minLeadDays = Math.max(0, minLeadDays);
        this.bookingWindowDays = Math.max(1, bookingWindowDays);
        this.maxQuantity = Math.max(1, maxQuantity);
    }

    /** Everything off, defaults for the rest: what a deployment with no dairy settings gets. */
    public static DairyProperties defaults() {
        return new DairyProperties(false, false, 1, 7, 20);
    }

    public boolean subscriptionsEnabled() { return subscriptionsEnabled; }
    public boolean subscriptionJobEnabled() { return subscriptionJobEnabled; }
    /** Earliest delivery for a one-time dairy order is this many days after today (1 = tomorrow). */
    public int minLeadDays() { return minLeadDays; }
    /** How many days from the earliest date a customer can pick. */
    public int bookingWindowDays() { return bookingWindowDays; }
    public int maxQuantity() { return maxQuantity; }
}
