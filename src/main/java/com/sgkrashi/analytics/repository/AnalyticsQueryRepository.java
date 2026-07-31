package com.sgkrashi.analytics.repository;

import com.sgkrashi.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Every method here is a real SQL-level {@code GROUP BY}/{@code SUM}/{@code COUNT}
 * aggregate query — none of them fetch entity lists for Java-side summing.
 * Extends {@code JpaRepository<Payment, Long>} purely as Spring Data's
 * required base type (revenue's own primary table); every other method here
 * queries unrelated tables via native SQL and owns no entity of its own —
 * mirrors Module 18's {@code SearchService}, which fans out to existing
 * repositories rather than owning anything itself.
 */
public interface AnalyticsQueryRepository extends JpaRepository<Payment, Long> {

    /**
     * One row per (date bucket, payable type). {@code datePattern} is always
     * one of three fixed literals chosen in {@code AnalyticsServiceImpl}
     * ({@code "%Y-%m-%d"}/{@code "%x-%v"}/{@code "%Y-%m"}) and passed as a
     * bound parameter — never string-concatenated into the query — so this
     * is not a SQL-injection surface despite driving {@code DATE_FORMAT}'s
     * format string dynamically.
     */
    @Query(value = """
            SELECT DATE_FORMAT(p.created_at, :datePattern) AS bucket,
                   p.payable_type AS payableType,
                   SUM(p.amount) AS total
            FROM payments p
            WHERE p.status = 'PAID'
              AND p.created_at >= :from
              AND p.created_at < :to
            GROUP BY bucket, p.payable_type
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> findRevenueByBucket(@Param("datePattern") String datePattern, @Param("from") Instant from, @Param("to") Instant to);

    /** Top products by revenue — only orders that were genuinely paid at some point (CONFIRMED or later-REFUNDED), never PENDING_PAYMENT/PAYMENT_FAILED. */
    @Query(value = """
            SELECT p.id AS id, p.name AS name, SUM(oi.quantity) AS units, SUM(oi.line_total) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN products p ON p.id = oi.product_id
            WHERE oi.item_type = 'PRODUCT'
              AND o.status IN ('CONFIRMED', 'REFUNDED')
              AND o.created_at >= :from AND o.created_at < :to
            GROUP BY p.id, p.name
            ORDER BY revenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopProducts(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    /** Same shape as {@link #findTopProducts} for the other order-item-based catalog type. */
    @Query(value = """
            SELECT c.id AS id, c.name AS name, SUM(oi.quantity) AS units, SUM(oi.line_total) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN crop_listings c ON c.id = oi.crop_listing_id
            WHERE oi.item_type = 'CROP_LISTING'
              AND o.status IN ('CONFIRMED', 'REFUNDED')
              AND o.created_at >= :from AND o.created_at < :to
            GROUP BY c.id, c.name
            ORDER BY revenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopCropListings(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    /** "Units" here is booking count, not nights — see {@code AnalyticsServiceImpl}'s mapping. CONFIRMED or COMPLETED only (never PENDING_PAYMENT/CANCELLED). */
    @Query(value = """
            SELECT e.id AS id, e.name AS name, COUNT(*) AS bookingCount, SUM(b.total_price) AS revenue
            FROM bookings b
            JOIN equipment e ON e.id = b.bookable_id
            WHERE b.bookable_type = 'EQUIPMENT'
              AND b.status IN ('CONFIRMED', 'COMPLETED')
              AND b.created_at >= :from AND b.created_at < :to
            GROUP BY e.id, e.name
            ORDER BY revenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopEquipment(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    /** Same shape as {@link #findTopEquipment} for the other bookable type. */
    @Query(value = """
            SELECT s.id AS id, s.name AS name, COUNT(*) AS bookingCount, SUM(b.total_price) AS revenue
            FROM bookings b
            JOIN stay_listings s ON s.id = b.bookable_id
            WHERE b.bookable_type = 'STAY'
              AND b.status IN ('CONFIRMED', 'COMPLETED')
              AND b.created_at >= :from AND b.created_at < :to
            GROUP BY s.id, s.name
            ORDER BY revenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopStayListings(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    /**
     * Booked days per listing WITHIN the requested window, computed as the
     * overlap between each CONFIRMED booking's {@code [start_date, end_date)}
     * and the window's {@code [from, to)} — {@code GREATEST}/{@code LEAST}
     * clip each booking to the window before {@code DATEDIFF} measures it, so
     * a booking that only partially overlaps the window isn't over-counted.
     * Same inclusive-start/exclusive-end convention {@code Booking} itself
     * documents.
     */
    @Query(value = """
            SELECT b.bookable_id AS bookableId,
                   SUM(GREATEST(0, DATEDIFF(LEAST(b.end_date, :to), GREATEST(b.start_date, :from)))) AS bookedDays
            FROM bookings b
            WHERE b.bookable_type = :bookableType
              AND b.status = 'CONFIRMED'
              AND b.start_date < :to
              AND b.end_date > :from
            GROUP BY b.bookable_id
            """, nativeQuery = true)
    List<Object[]> findBookedDaysByBookable(@Param("bookableType") String bookableType, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** One row per module type; {@code AnalyticsServiceImpl} sums these (already-aggregated numbers, not raw entities) for the overall total. */
    @Query(value = """
            SELECT i.module_type AS moduleType,
                   COUNT(*) AS total,
                   SUM(CASE WHEN i.status = 'CONVERTED' THEN 1 ELSE 0 END) AS converted
            FROM inquiries i
            WHERE i.created_at >= :from AND i.created_at < :to
            GROUP BY i.module_type
            """, nativeQuery = true)
    List<Object[]> findConversionByModuleType(@Param("from") Instant from, @Param("to") Instant to);
}
