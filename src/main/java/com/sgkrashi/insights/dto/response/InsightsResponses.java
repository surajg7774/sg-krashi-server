package com.sgkrashi.insights.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response shapes for the admin Insights page. Every number here is a count or
 * average over rows that already exist in the database; nothing is estimated.
 * {@code bucket} is the first day of the bucket as {@code yyyy-MM-dd} in India time
 * (Asia/Kolkata), whatever the granularity: the day itself, the Monday of the week,
 * or the first of the month.
 */
public final class InsightsResponses {

    private InsightsResponses() {
    }

    /** Signups per bucket. {@code deleted} = accounts created in the bucket that have since been deleted (their sign-in method is no longer known, so they are not in email/google). */
    public record SignupsResponse(String groupBy, List<SignupPoint> points, SignupTotals totals) {
        public record SignupPoint(String bucket, long total, long email, long google, long deleted) {
        }

        public record SignupTotals(long total, long email, long google, long deleted) {
        }
    }

    public record OrdersResponse(String groupBy, List<OrderPoint> points, OrderTotals totals, RepeatCustomers repeatCustomers) {
        /** {@code paid} = CONFIRMED, SHIPPED, DELIVERED or REFUNDED (a refunded order was paid first); {@code pending} = still waiting for payment. */
        public record OrderPoint(String bucket, long created, long paid, long failed, long pending) {
        }

        /** {@code checkoutToPaidRate} = paid / created in the window (null when nothing was created); {@code averageOrderValue} = mean total of the paid orders (null when none). */
        public record OrderTotals(long created, long paid, long failed, long pending,
                                  Double checkoutToPaidRate, BigDecimal averageOrderValue) {
        }

        /** All-time, not limited to the window: customers who ever paid, and how many paid at least twice. */
        public record RepeatCustomers(long customersWhoPaid, long repeatCustomers, Double repeatRate) {
        }
    }

    /** Time between order milestones, from order_status_history, for orders placed in the window. */
    public record FulfilmentResponse(List<Stage> stages) {
        /** {@code orders} = how many orders have both milestones; median and average are in seconds and null when it is 0 (payment often takes seconds, so hours would hide it). */
        public record Stage(String key, String label, long orders, Long medianSeconds, Long averageSeconds) {
        }
    }

    public record BookingsResponse(String groupBy, List<BookingPoint> points, BookingTotals totals) {
        public record BookingPoint(String bucket, long created, long pending, long confirmed, long completed, long cancelled,
                                   long equipment, long stay) {
        }

        /** {@code confirmedValue} = total price of CONFIRMED and COMPLETED bookings created in the window. */
        public record BookingTotals(long created, long pending, long confirmed, long completed, long cancelled,
                                    long equipment, long stay, BigDecimal confirmedValue) {
        }
    }

    /**
     * Logged-in activity and usage of AI features. Limits that the page also states: only
     * logged-in people have accounts, so {@code activeUsers} never counts guests; accounts that were
     * deleted had their sessions, chats and scans removed, so those are missing from every number here;
     * guest Crop Doctor scans are not stored at all.
     */
    public record ActivityResponse(String groupBy, List<ActivePoint> activeUsers, List<ChatPoint> chat,
                                   List<ScanPoint> cropDoctor, ActiveNow activeNow) {
        public record ActivePoint(String bucket, long users) {
        }

        public record ChatPoint(String bucket, long guestSessions, long loggedInSessions, long messagesFromPeople) {
        }

        public record ScanPoint(String bucket, long scans) {
        }

        /** Distinct logged-in people who started or renewed a session in the last 7 and 30 India-time days (today included). */
        public record ActiveNow(long last7Days, long last30Days) {
        }
    }

    /**
     * Anonymous daily feature counters (usage_daily). {@code countingSince} is the first day any count was
     * written (null if none yet); buckets before it are left out, since nothing was being counted then. Each
     * point maps every feature key to its count for that bucket (0 when unused). Counts are requests answered
     * by the server, not people, and may be up to a minute behind.
     */
    public record UsageResponse(String groupBy, String countingSince, List<UsagePoint> points, Map<String, Long> totals) {
        public record UsagePoint(String bucket, Map<String, Long> counts) {
        }
    }

    /** Current state, not tied to the date range. */
    public record SnapshotResponse(Accounts accounts, PushDevices pushDevices, Carts carts) {
        public record Accounts(long total, long deleted, long active) {
        }

        /** Registered, active push tokens; a person with two phones counts as two devices and one person. */
        public record PushDevices(long devices, long people, long android, long ios) {
        }

        /** Carts that hold at least one item right now; {@code touchedLast7Days} = changed in the last 7 days. */
        public record Carts(long withItems, long totalItems, long touchedLast7Days) {
        }
    }
}
