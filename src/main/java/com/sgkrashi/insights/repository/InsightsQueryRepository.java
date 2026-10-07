package com.sgkrashi.insights.repository;

import com.sgkrashi.insights.util.Granularity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only SQL behind the admin Insights page: plain COUNT/SUM/GROUP BY over existing tables, no writes.
 *
 * <p><b>India time.</b> Every timestamp column is converted with
 * {@code CONVERT_TZ(col, @@session.time_zone, '+05:30')} before it is bucketed, and every date range is passed
 * as an India wall-clock string and converted back with the same session zone for the comparison. So the
 * result does not depend on the database server's or the JVM's time zone, and does not need MySQL's named-zone
 * tables ({@code +05:30} is a fixed offset; India has no daylight saving).
 *
 * <p>The window is {@code [from 00:00, toExclusive 00:00)} in India time. The bucket expressions are fixed
 * literals chosen from {@link Granularity}, never request text.
 */
@Repository
public class InsightsQueryRepository {

    private static final DateTimeFormatter WALL_CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Order statuses that mean the customer paid (REFUNDED was paid first), same set the revenue reports use. */
    private static final String PAID = "('CONFIRMED','SHIPPED','DELIVERED','REFUNDED')";

    /** Placeholder email pattern written by account deletion ({@code AccountErasureRepository}). */
    private static final String DELETED_USER = "u.email LIKE 'deleted-%@deleted.invalid'";

    private static final int FULFILMENT_ROW_CAP = 10_000;

    private final NamedParameterJdbcTemplate jdbc;

    public InsightsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- row shapes ----------------------------------------------------------------------------------------

    public record SignupRow(String bucket, long total, long email, long google, long deleted) {
    }

    public record OrderRow(String bucket, long created, long paid, long failed, long pending, BigDecimal paidAmount) {
    }

    public record RepeatRow(long customersWhoPaid, long repeatCustomers) {
    }

    /** Seconds between milestones; a null means the order has not reached that milestone. */
    public record FulfilmentRow(Long placedToPaid, Long paidToShipped, Long shippedToDelivered, Long paidToDelivered) {
    }

    public record BookingRow(String bucket, long created, long pending, long confirmed, long completed, long cancelled,
                             long equipment, long stay, BigDecimal confirmedValue) {
    }

    public record CountRow(String bucket, long count) {
    }

    public record ChatRow(String bucket, long guestSessions, long loggedInSessions, long messagesFromPeople) {
    }

    // ---- SQL helpers ---------------------------------------------------------------------------------------

    private static String ist(String column) {
        return "CONVERT_TZ(" + column + ", @@session.time_zone, '+05:30')";
    }

    private static String window(String column) {
        return column + " >= CONVERT_TZ(:from, '+05:30', @@session.time_zone) AND "
                + column + " < CONVERT_TZ(:to, '+05:30', @@session.time_zone)";
    }

    private static String bucket(Granularity granularity, String column) {
        String day = "DATE(" + ist(column) + ")";
        return switch (granularity) {
            case DAY -> "DATE_FORMAT(" + day + ", '%Y-%m-%d')";
            case WEEK -> "DATE_FORMAT(DATE_SUB(" + day + ", INTERVAL WEEKDAY(" + day + ") DAY), '%Y-%m-%d')";
            case MONTH -> "DATE_FORMAT(DATE_SUB(" + day + ", INTERVAL DAYOFMONTH(" + day + ") - 1 DAY), '%Y-%m-%d')";
        };
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate toInclusive) {
        return new MapSqlParameterSource()
                .addValue("from", from.atStartOfDay().format(WALL_CLOCK))
                .addValue("to", toInclusive.plusDays(1).atStartOfDay().format(WALL_CLOCK));
    }

    // ---- signups -------------------------------------------------------------------------------------------

    public List<SignupRow> signups(Granularity g, LocalDate from, LocalDate to) {
        String sql = "SELECT " + bucket(g, "u.created_at") + " AS bucket, COUNT(*) AS total, "
                + "SUM(CASE WHEN " + DELETED_USER + " THEN 0 WHEN u.google_id IS NULL THEN 1 ELSE 0 END) AS email_n, "
                + "SUM(CASE WHEN " + DELETED_USER + " THEN 0 WHEN u.google_id IS NOT NULL THEN 1 ELSE 0 END) AS google_n, "
                + "SUM(CASE WHEN " + DELETED_USER + " THEN 1 ELSE 0 END) AS deleted_n "
                + "FROM users u WHERE " + window("u.created_at") + " GROUP BY bucket ORDER BY bucket";
        return jdbc.query(sql, range(from, to), (rs, i) ->
                new SignupRow(rs.getString("bucket"), rs.getLong("total"), rs.getLong("email_n"), rs.getLong("google_n"), rs.getLong("deleted_n")));
    }

    // ---- orders --------------------------------------------------------------------------------------------

    public List<OrderRow> orders(Granularity g, LocalDate from, LocalDate to) {
        String sql = "SELECT " + bucket(g, "o.created_at") + " AS bucket, COUNT(*) AS created, "
                + "SUM(CASE WHEN o.status IN " + PAID + " THEN 1 ELSE 0 END) AS paid, "
                + "SUM(CASE WHEN o.status = 'PAYMENT_FAILED' THEN 1 ELSE 0 END) AS failed, "
                + "SUM(CASE WHEN o.status = 'PENDING_PAYMENT' THEN 1 ELSE 0 END) AS pending, "
                + "COALESCE(SUM(CASE WHEN o.status IN " + PAID + " THEN o.total_amount ELSE 0 END), 0) AS paid_amount "
                + "FROM orders o WHERE " + window("o.created_at") + " GROUP BY bucket ORDER BY bucket";
        return jdbc.query(sql, range(from, to), (rs, i) ->
                new OrderRow(rs.getString("bucket"), rs.getLong("created"), rs.getLong("paid"), rs.getLong("failed"),
                        rs.getLong("pending"), rs.getBigDecimal("paid_amount")));
    }

    /** All-time: how many customers ever paid for an order, and how many did so at least twice. */
    public RepeatRow repeatCustomers() {
        String sql = "SELECT COUNT(*) AS customers, COALESCE(SUM(CASE WHEN n >= 2 THEN 1 ELSE 0 END), 0) AS repeaters FROM ("
                + "SELECT user_id, COUNT(*) AS n FROM orders WHERE status IN " + PAID + " GROUP BY user_id) t";
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, i) -> new RepeatRow(rs.getLong("customers"), rs.getLong("repeaters")));
    }

    public List<FulfilmentRow> fulfilment(LocalDate from, LocalDate to) {
        String sql = "SELECT TIMESTAMPDIFF(SECOND, o.created_at, c.at) AS placed_to_paid, "
                + "TIMESTAMPDIFF(SECOND, c.at, s.at) AS paid_to_shipped, "
                + "TIMESTAMPDIFF(SECOND, s.at, d.at) AS shipped_to_delivered, "
                + "TIMESTAMPDIFF(SECOND, c.at, d.at) AS paid_to_delivered "
                + "FROM orders o "
                + "LEFT JOIN (SELECT order_id, MIN(created_at) AS at FROM order_status_history WHERE status = 'CONFIRMED' GROUP BY order_id) c ON c.order_id = o.id "
                + "LEFT JOIN (SELECT order_id, MIN(created_at) AS at FROM order_status_history WHERE status = 'SHIPPED' GROUP BY order_id) s ON s.order_id = o.id "
                + "LEFT JOIN (SELECT order_id, MIN(created_at) AS at FROM order_status_history WHERE status = 'DELIVERED' GROUP BY order_id) d ON d.order_id = o.id "
                + "WHERE " + window("o.created_at") + " ORDER BY o.id DESC LIMIT " + FULFILMENT_ROW_CAP;
        return jdbc.query(sql, range(from, to), (rs, i) -> new FulfilmentRow(
                nullableLong(rs.getObject("placed_to_paid")), nullableLong(rs.getObject("paid_to_shipped")),
                nullableLong(rs.getObject("shipped_to_delivered")), nullableLong(rs.getObject("paid_to_delivered"))));
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    // ---- bookings ------------------------------------------------------------------------------------------

    public List<BookingRow> bookings(Granularity g, LocalDate from, LocalDate to) {
        String sql = "SELECT " + bucket(g, "b.created_at") + " AS bucket, COUNT(*) AS created, "
                + "SUM(CASE WHEN b.status = 'PENDING_PAYMENT' THEN 1 ELSE 0 END) AS pending, "
                + "SUM(CASE WHEN b.status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed, "
                + "SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed, "
                + "SUM(CASE WHEN b.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled, "
                + "SUM(CASE WHEN b.bookable_type = 'EQUIPMENT' THEN 1 ELSE 0 END) AS equipment, "
                + "SUM(CASE WHEN b.bookable_type = 'STAY' THEN 1 ELSE 0 END) AS stay, "
                + "COALESCE(SUM(CASE WHEN b.status IN ('CONFIRMED','COMPLETED') THEN b.total_price ELSE 0 END), 0) AS confirmed_value "
                + "FROM bookings b WHERE " + window("b.created_at") + " GROUP BY bucket ORDER BY bucket";
        return jdbc.query(sql, range(from, to), (rs, i) ->
                new BookingRow(rs.getString("bucket"), rs.getLong("created"), rs.getLong("pending"), rs.getLong("confirmed"),
                        rs.getLong("completed"), rs.getLong("cancelled"), rs.getLong("equipment"), rs.getLong("stay"),
                        rs.getBigDecimal("confirmed_value")));
    }

    // ---- activity ------------------------------------------------------------------------------------------

    /** Distinct logged-in people with a refresh token created in each bucket (a login or a session renewal). */
    public List<CountRow> activeUsers(Granularity g, LocalDate from, LocalDate to) {
        String sql = "SELECT " + bucket(g, "t.created_at") + " AS bucket, COUNT(DISTINCT t.user_id) AS n "
                + "FROM refresh_tokens t WHERE " + window("t.created_at") + " GROUP BY bucket ORDER BY bucket";
        return jdbc.query(sql, range(from, to), (rs, i) -> new CountRow(rs.getString("bucket"), rs.getLong("n")));
    }

    public long activeUsersBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT COUNT(DISTINCT t.user_id) FROM refresh_tokens t WHERE " + window("t.created_at");
        Long n = jdbc.queryForObject(sql, range(from, to), Long.class);
        return n == null ? 0 : n;
    }

    public List<ChatRow> chat(Granularity g, LocalDate from, LocalDate to) {
        String sessions = "SELECT " + bucket(g, "s.created_at") + " AS bucket, "
                + "SUM(CASE WHEN s.user_id IS NULL THEN 1 ELSE 0 END) AS guest_n, "
                + "SUM(CASE WHEN s.user_id IS NOT NULL THEN 1 ELSE 0 END) AS user_n "
                + "FROM chat_sessions s WHERE " + window("s.created_at") + " GROUP BY bucket";
        String messages = "SELECT " + bucket(g, "m.created_at") + " AS bucket, COUNT(*) AS n "
                + "FROM chat_messages m WHERE m.role = 'USER' AND " + window("m.created_at") + " GROUP BY bucket";
        java.util.Map<String, long[]> merged = new java.util.TreeMap<>();
        jdbc.query(sessions, range(from, to), rs -> {
            long[] v = merged.computeIfAbsent(rs.getString("bucket"), k -> new long[3]);
            v[0] = rs.getLong("guest_n");
            v[1] = rs.getLong("user_n");
        });
        jdbc.query(messages, range(from, to), rs -> {
            merged.computeIfAbsent(rs.getString("bucket"), k -> new long[3])[2] = rs.getLong("n");
        });
        return merged.entrySet().stream().map(e -> new ChatRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
    }

    public List<CountRow> cropScans(Granularity g, LocalDate from, LocalDate to) {
        String sql = "SELECT " + bucket(g, "c.created_at") + " AS bucket, COUNT(*) AS n "
                + "FROM crop_scans c WHERE " + window("c.created_at") + " GROUP BY bucket ORDER BY bucket";
        return jdbc.query(sql, range(from, to), (rs, i) -> new CountRow(rs.getString("bucket"), rs.getLong("n")));
    }

    // ---- feature usage (usage_daily) -----------------------------------------------------------------------

    public record UsageRow(String bucket, String feature, long count) {
    }

    /** usage_daily.day is already an India calendar day, so no time-zone conversion is needed here. */
    public List<UsageRow> usage(Granularity g, LocalDate from, LocalDate to) {
        String day = "d.day";
        String bucket = switch (g) {
            case DAY -> "DATE_FORMAT(" + day + ", '%Y-%m-%d')";
            case WEEK -> "DATE_FORMAT(DATE_SUB(" + day + ", INTERVAL WEEKDAY(" + day + ") DAY), '%Y-%m-%d')";
            case MONTH -> "DATE_FORMAT(DATE_SUB(" + day + ", INTERVAL DAYOFMONTH(" + day + ") - 1 DAY), '%Y-%m-%d')";
        };
        String sql = "SELECT " + bucket + " AS bucket, d.feature AS feature, SUM(d.`count`) AS n FROM usage_daily d "
                + "WHERE d.day >= :fromDay AND d.day <= :toDay GROUP BY bucket, d.feature ORDER BY bucket";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("fromDay", from.toString()).addValue("toDay", to.toString());
        return jdbc.query(sql, params, (rs, i) -> new UsageRow(rs.getString("bucket"), rs.getString("feature"), rs.getLong("n")));
    }

    /** The first day any count was written, as yyyy-MM-dd, or null when nothing has been counted yet. */
    public String usageCountingSince() {
        return jdbc.queryForObject("SELECT DATE_FORMAT(MIN(day), '%Y-%m-%d') FROM usage_daily", new MapSqlParameterSource(), String.class);
    }

    // ---- snapshot ------------------------------------------------------------------------------------------

    public long[] accountTotals() {
        String sql = "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN " + DELETED_USER + " THEN 1 ELSE 0 END), 0) AS deleted FROM users u";
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, i) -> new long[]{rs.getLong("total"), rs.getLong("deleted")});
    }

    /** {devices, people, android, ios} among active push tokens. */
    public long[] pushDevices() {
        String sql = "SELECT COUNT(*) AS devices, COUNT(DISTINCT user_id) AS people, "
                + "COALESCE(SUM(CASE WHEN platform = 'ANDROID' THEN 1 ELSE 0 END), 0) AS android, "
                + "COALESCE(SUM(CASE WHEN platform = 'IOS' THEN 1 ELSE 0 END), 0) AS ios "
                + "FROM device_tokens WHERE is_active = TRUE";
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, i) ->
                new long[]{rs.getLong("devices"), rs.getLong("people"), rs.getLong("android"), rs.getLong("ios")});
    }

    /** {cartsWithItems, totalItems, touchedLast7Days}; the 7 days are measured from {@code sevenDaysAgo} (an India wall-clock day start). */
    public long[] carts(LocalDate sevenDaysAgo) {
        String sql = "SELECT COUNT(DISTINCT c.id) AS carts, COALESCE(SUM(ci.quantity), 0) AS items, "
                + "COUNT(DISTINCT CASE WHEN c.updated_at >= CONVERT_TZ(:since, '+05:30', @@session.time_zone) THEN c.id END) AS recent "
                + "FROM carts c JOIN cart_items ci ON ci.cart_id = c.id WHERE c.is_active = TRUE";
        MapSqlParameterSource params = new MapSqlParameterSource("since", sevenDaysAgo.atStartOfDay().format(WALL_CLOCK));
        return jdbc.queryForObject(sql, params, (rs, i) -> new long[]{rs.getLong("carts"), rs.getLong("items"), rs.getLong("recent")});
    }
}
