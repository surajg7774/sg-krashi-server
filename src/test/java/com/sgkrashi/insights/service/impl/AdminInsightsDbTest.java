package com.sgkrashi.insights.service.impl;

import com.sgkrashi.insights.dto.response.InsightsResponses.ActivityResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.BookingsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.FulfilmentResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.OrdersResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SignupsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SnapshotResponse;
import com.sgkrashi.insights.repository.InsightsQueryRepository;
import com.sgkrashi.insights.util.Granularity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real Insights SQL against a real MySQL with hand-computed synthetic data. Off by default (the normal
 * build has no database); to run it, start a throwaway MySQL 9.4, apply the Flyway migrations, and pass
 * {@code -Dinsights.db.url=jdbc:mysql://localhost:3399/insights -Dinsights.db.user=root -Dinsights.db.password=...}.
 *
 * <p>It <b>deletes every row</b> of the tables it uses, so it refuses any host other than localhost.
 *
 * <p>Every expectation below is worked out by hand from the rows in {@link #seed}. The rows deliberately sit on
 * India-time boundaries (23:30 vs 00:30, Sunday 23:59:59 vs Monday 00:00, 30 Sep vs 1 Oct) because those are the
 * places a UTC-based query would put a row in the wrong bucket; the test is meant to be run against a server whose
 * own time zone is not UTC as well.
 */
@EnabledIfSystemProperty(named = "insights.db.url", matches = ".+")
class AdminInsightsDbTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private static JdbcTemplate jdbc;
    private static AdminInsightsServiceImpl service;

    /** Written as India wall-clock time and converted to whatever the session zone is, like the queries do. */
    private static String ist(String wallClock) {
        return "CONVERT_TZ('" + wallClock + "', '+05:30', @@session.time_zone)";
    }

    @BeforeAll
    static void connectAndSeed() {
        String url = System.getProperty("insights.db.url");
        if (!(url.contains("//localhost") || url.contains("//127.0.0.1"))) {
            throw new IllegalStateException("Refusing to run: this test deletes rows and may only target a local throwaway database");
        }
        var dataSource = new SingleConnectionDataSource(url, System.getProperty("insights.db.user", "root"),
                System.getProperty("insights.db.password", ""), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        seed();
        // "Now" is 2026-10-06 12:00 India time.
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T06:30:00Z"), java.time.ZoneId.of("Asia/Kolkata"));
        service = new AdminInsightsServiceImpl(new InsightsQueryRepository(new NamedParameterJdbcTemplate(jdbc)), clock);
    }

    private static void seed() {
        for (String table : List.of("order_status_history", "order_items", "orders", "bookings", "cart_items", "carts", "chat_messages",
                "chat_sessions", "crop_scans", "device_tokens", "refresh_tokens", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        // users: id, email, google_id, created (India time)
        user(1, "u1@x.test", null, "2026-09-05 23:30:00");   // email, 5 Sep (UTC day is also 5 Sep)
        user(2, "u2@x.test", null, "2026-09-06 00:30:00");   // email, 6 Sep India = 5 Sep UTC
        user(3, "u3@x.test", "g3", "2026-09-06 10:00:00");   // google
        user(4, "deleted-4@deleted.invalid", null, "2026-09-07 12:00:00"); // deleted account
        user(5, "u5@x.test", "g5", "2026-09-13 23:59:59");   // google, Sunday night: week of 7 Sep
        user(6, "u6@x.test", null, "2026-09-14 00:00:00");   // email, Monday 00:00: week of 14 Sep
        user(7, "u7@x.test", null, "2026-09-30 23:59:59");   // email, last second of September
        user(8, "u8@x.test", null, "2026-10-01 00:00:00");   // email, first second of October

        // orders: id, user, status, amount, created
        order(1, 1, "CONFIRMED", "100.00", "2026-09-05 23:30:00");
        order(2, 1, "DELIVERED", "300.00", "2026-09-06 00:30:00");
        order(3, 2, "PAYMENT_FAILED", "50.00", "2026-09-06 10:00:00");
        order(4, 3, "PENDING_PAYMENT", "70.00", "2026-09-06 11:00:00");
        order(5, 3, "REFUNDED", "200.00", "2026-09-13 09:00:00");
        order(6, 2, "SHIPPED", "150.00", "2026-10-01 00:00:00");
        history(1, "PENDING_PAYMENT", "2026-09-05 23:30:00");
        history(1, "CONFIRMED", "2026-09-06 00:00:00");          // +0.5 h
        history(2, "PENDING_PAYMENT", "2026-09-06 00:30:00");
        history(2, "CONFIRMED", "2026-09-06 01:30:00");          // +1 h
        history(2, "SHIPPED", "2026-09-07 02:30:00");            // +25 h after payment
        history(2, "DELIVERED", "2026-09-09 02:30:00");          // +48 h after shipping, 73 h after payment
        history(5, "CONFIRMED", "2026-09-13 11:00:00");          // +2 h
        history(6, "CONFIRMED", "2026-10-01 00:30:00");          // +0.5 h

        // bookings: id, type, status, price, created
        booking(1, "EQUIPMENT", "CONFIRMED", "1000.00", "2026-09-05 23:30:00");
        booking(2, "STAY", "COMPLETED", "2000.00", "2026-09-06 00:30:00");
        booking(3, "STAY", "CANCELLED", "500.00", "2026-09-06 10:00:00");
        booking(4, "EQUIPMENT", "PENDING_PAYMENT", "300.00", "2026-10-01 00:00:00");

        // refresh tokens: user, created
        token(1, "2026-09-05 10:00:00");
        token(1, "2026-09-06 11:00:00");
        token(2, "2026-09-06 12:00:00");
        token(1, "2026-09-08 09:00:00");
        token(5, "2026-09-10 09:00:00");
        token(3, "2026-09-30 08:00:00");
        token(2, "2026-10-05 22:00:00");
        token(1, "2026-10-05 22:30:00");

        // chat: sessions (guest = null user) and messages
        jdbc.update("INSERT INTO chat_sessions (id, user_id, created_at, updated_at) VALUES (1, NULL, " + ist("2026-09-05 23:30:00") + ", NOW(6))");
        jdbc.update("INSERT INTO chat_sessions (id, user_id, created_at, updated_at) VALUES (2, 1, " + ist("2026-09-06 00:30:00") + ", NOW(6))");
        jdbc.update("INSERT INTO chat_sessions (id, user_id, created_at, updated_at) VALUES (3, 2, " + ist("2026-09-06 09:00:00") + ", NOW(6))");
        message(1, "USER", "2026-09-05 23:31:00");
        message(1, "ASSISTANT", "2026-09-05 23:31:05");
        message(2, "USER", "2026-09-06 00:31:00");
        message(2, "USER", "2026-09-06 00:40:00");
        message(3, "USER", "2026-09-06 09:01:00");

        // crop scans
        scan(1, "2026-09-06 00:30:00");
        scan(1, "2026-09-06 15:00:00");
        scan(2, "2026-10-01 00:00:00");

        // push devices: 3 active on 2 people, 1 inactive
        device(1, "t1", "ANDROID", true);
        device(1, "t2", "ANDROID", true);
        device(2, "t3", "IOS", true);
        device(3, "t4", "ANDROID", false);

        // carts: u1 has items and was touched recently, u2 has an item but not recently, u3 is empty
        cart(1, 1, "2026-10-05 10:00:00");
        cart(2, 2, "2026-09-20 10:00:00");
        cart(3, 3, "2026-10-05 11:00:00");
        item(1, 11, 2);
        item(1, 12, 3);
        item(2, 11, 1);
    }

    private static void user(long id, String email, String googleId, String created) {
        jdbc.update("INSERT INTO users (id, name, email, google_id, created_at, updated_at) VALUES (?, 'T', ?, ?, " + ist(created) + ", NOW(6))", id, email, googleId);
    }

    private static void order(long id, long userId, String status, String amount, String created) {
        jdbc.update("INSERT INTO orders (id, user_id, order_number, status, total_amount, shipping_line1, shipping_city, shipping_state, shipping_pincode, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'a', 'c', 's', '000000', " + ist(created) + ", NOW(6))", id, userId, "SK-" + id, status, new BigDecimal(amount));
    }

    private static void history(long orderId, String status, String created) {
        jdbc.update("INSERT INTO order_status_history (order_id, status, created_at) VALUES (?, ?, " + ist(created) + ")", orderId, status);
    }

    private static void booking(long id, String type, String status, String price, String created) {
        jdbc.update("INSERT INTO bookings (id, user_id, bookable_type, bookable_id, start_date, end_date, status, total_price, created_at, updated_at) "
                + "VALUES (?, 1, ?, 1, '2026-11-01', '2026-11-02', ?, ?, " + ist(created) + ", NOW(6))", id, type, status, new BigDecimal(price));
    }

    private static int tokenSeq = 0;

    private static void token(long userId, String created) {
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at, updated_at) VALUES (?, ?, NOW(6), " + ist(created) + ", NOW(6))", userId, "h" + (++tokenSeq));
    }

    private static void message(long sessionId, String role, String created) {
        jdbc.update("INSERT INTO chat_messages (session_id, role, content, created_at, updated_at) VALUES (?, ?, 'x', " + ist(created) + ", NOW(6))", sessionId, role);
    }

    private static void scan(long userId, String created) {
        jdbc.update("INSERT INTO crop_scans (user_id, image_url, crop_name, model_version, created_at, updated_at) VALUES (?, 'u', 'c', 'm', " + ist(created) + ", NOW(6))", userId);
    }

    private static void device(long userId, String token, String platform, boolean active) {
        jdbc.update("INSERT INTO device_tokens (user_id, token, platform, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(6), NOW(6))", userId, token, platform, active);
    }

    private static void cart(long id, long userId, String updated) {
        jdbc.update("INSERT INTO carts (id, user_id, created_at, updated_at) VALUES (?, ?, NOW(6), " + ist(updated) + ")", id, userId);
    }

    private static void item(long cartId, long productId, int quantity) {
        jdbc.update("INSERT INTO cart_items (cart_id, item_type, product_id, quantity) VALUES (?, 'PRODUCT', ?, ?)", cartId, productId, quantity);
    }

    private static <T> T at(List<T> points, int index) {
        return points.get(index);
    }

    // ---- signups -------------------------------------------------------------------------------------------

    @Test
    void signupsPerDayUseIndiaDaysNotUtcDays() {
        SignupsResponse r = service.signups(FROM, TO, Granularity.DAY);
        assertEquals(61, r.points().size(), "1 Sep to 31 Oct, every day present, zero-filled");
        assertEquals(new SignupsResponse.SignupPoint("2026-09-05", 1, 1, 0, 0), r.points().get(4));
        // u2 signed up at 00:30 India time on the 6th (19:00 UTC on the 5th): it belongs to the 6th.
        assertEquals(new SignupsResponse.SignupPoint("2026-09-06", 2, 1, 1, 0), r.points().get(5));
        assertEquals(new SignupsResponse.SignupPoint("2026-09-07", 1, 0, 0, 1), r.points().get(6));
        assertEquals(new SignupsResponse.SignupPoint("2026-09-02", 0, 0, 0, 0), r.points().get(1));
        assertEquals(new SignupsResponse.SignupTotals(8, 5, 2, 1), r.totals());
    }

    @Test
    void signupsPerWeekStartOnMondayInIndiaTime() {
        SignupsResponse r = service.signups(FROM, TO, Granularity.WEEK);
        assertEquals("2026-08-31", at(r.points(), 0).bucket(), "the week that contains 1 Sep starts Monday 31 Aug");
        assertEquals(3, at(r.points(), 0).total());      // u1, u2, u3
        assertEquals("2026-09-07", at(r.points(), 1).bucket());
        assertEquals(2, at(r.points(), 1).total());      // u4 (deleted) and u5 (Sunday 23:59:59 India time still belongs to this week)
        assertEquals("2026-09-14", at(r.points(), 2).bucket());
        assertEquals(1, at(r.points(), 2).total());      // u6, Monday 00:00:00
        assertEquals("2026-09-28", at(r.points(), 4).bucket());
        assertEquals(2, at(r.points(), 4).total());      // u7 (30 Sep) and u8 (1 Oct) share a week
    }

    @Test
    void signupsPerMonthSplitAtMidnightIndiaTime() {
        SignupsResponse r = service.signups(FROM, TO, Granularity.MONTH);
        assertEquals(2, r.points().size());
        assertEquals(new SignupsResponse.SignupPoint("2026-09-01", 7, 4, 2, 1), r.points().get(0));
        assertEquals(new SignupsResponse.SignupPoint("2026-10-01", 1, 1, 0, 0), r.points().get(1));
    }

    // ---- orders --------------------------------------------------------------------------------------------

    @Test
    void ordersCountPaidFailedAndPendingAndComputeRates() {
        OrdersResponse r = service.orders(FROM, TO, Granularity.DAY);
        assertEquals(new OrdersResponse.OrderPoint("2026-09-05", 1, 1, 0, 0), r.points().get(4));
        assertEquals(new OrdersResponse.OrderPoint("2026-09-06", 3, 1, 1, 1), r.points().get(5));
        assertEquals(new OrdersResponse.OrderPoint("2026-09-13", 1, 1, 0, 0), r.points().get(12));
        assertEquals(new OrdersResponse.OrderPoint("2026-10-01", 1, 1, 0, 0), r.points().get(30));
        assertEquals(6, r.totals().created());
        assertEquals(4, r.totals().paid());              // CONFIRMED, DELIVERED, REFUNDED and SHIPPED all mean "was paid"
        assertEquals(1, r.totals().failed());
        assertEquals(1, r.totals().pending());
        assertEquals(0.6667, r.totals().checkoutToPaidRate());
        assertEquals(new BigDecimal("187.50"), r.totals().averageOrderValue()); // (100 + 300 + 200 + 150) / 4
        assertEquals(new OrdersResponse.RepeatCustomers(3, 1, 0.3333), r.repeatCustomers()); // u1 paid twice; u2 and u3 once
    }

    @Test
    void ordersInAnEmptyWindowGiveNullRatesNotZeroOrErrors() {
        OrdersResponse r = service.orders(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), Granularity.WEEK);
        assertEquals(0, r.totals().created());
        assertNull(r.totals().checkoutToPaidRate());
        assertNull(r.totals().averageOrderValue());
        assertTrue(r.points().stream().allMatch(p -> p.created() == 0));
    }

    @Test
    void fulfilmentUsesMedianAndAverageOfRealMilestones() {
        FulfilmentResponse r = service.fulfilment(FROM, TO);
        FulfilmentResponse.Stage placedToPaid = r.stages().get(0);
        assertEquals(4, placedToPaid.orders());           // orders 1, 2, 5, 6 reached payment
        assertEquals(2700L, placedToPaid.medianSeconds()); // sorted 0.5 h, 0.5 h, 1 h, 2 h -> median 0.75 h
        assertEquals(3600L, placedToPaid.averageSeconds()); // (0.5 + 1 + 2 + 0.5) / 4 = 1 h
        FulfilmentResponse.Stage paidToShipped = r.stages().get(1);
        assertEquals(1, paidToShipped.orders());
        assertEquals(25 * 3600L, paidToShipped.medianSeconds());
        FulfilmentResponse.Stage shippedToDelivered = r.stages().get(2);
        assertEquals(48 * 3600L, shippedToDelivered.medianSeconds());
        FulfilmentResponse.Stage paidToDelivered = r.stages().get(3);
        assertEquals(73 * 3600L, paidToDelivered.medianSeconds());
    }

    @Test
    void fulfilmentWithNoDataHasNullTimes() {
        FulfilmentResponse r = service.fulfilment(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertTrue(r.stages().stream().allMatch(s -> s.orders() == 0 && s.medianSeconds() == null && s.averageSeconds() == null));
    }

    // ---- bookings ------------------------------------------------------------------------------------------

    @Test
    void bookingsSplitByStatusAndTypeAndSumConfirmedValue() {
        BookingsResponse r = service.bookings(FROM, TO, Granularity.MONTH);
        assertEquals(new BookingsResponse.BookingPoint("2026-09-01", 3, 0, 1, 1, 1, 1, 2), r.points().get(0));
        assertEquals(new BookingsResponse.BookingPoint("2026-10-01", 1, 1, 0, 0, 0, 1, 0), r.points().get(1));
        assertEquals(new BookingsResponse.BookingTotals(4, 1, 1, 1, 1, 2, 2, new BigDecimal("3000.00")), r.totals());
    }

    // ---- activity ------------------------------------------------------------------------------------------

    @Test
    void activeUsersAreDistinctPeoplePerWeekAndRollingWindows() {
        ActivityResponse r = service.activity(FROM, TO, Granularity.WEEK);
        assertEquals(new ActivityResponse.ActivePoint("2026-08-31", 2), r.activeUsers().get(0)); // u1 (twice) and u2
        assertEquals(new ActivityResponse.ActivePoint("2026-09-07", 2), r.activeUsers().get(1)); // u1 and u5
        assertEquals(new ActivityResponse.ActivePoint("2026-09-28", 1), r.activeUsers().get(4)); // u3 on 30 Sep
        assertEquals(new ActivityResponse.ActivePoint("2026-10-05", 2), r.activeUsers().get(5)); // u2 and u1
        assertEquals(3, r.activeNow().last7Days());      // 30 Sep to 6 Oct: u3, u2, u1
        assertEquals(4, r.activeNow().last30Days());     // 7 Sep to 6 Oct: u1, u5, u3, u2
    }

    @Test
    void chatSeparatesGuestFromLoggedInAndCountsOnlyPeoplesMessages() {
        ActivityResponse r = service.activity(FROM, TO, Granularity.DAY);
        assertEquals(new ActivityResponse.ChatPoint("2026-09-05", 1, 0, 1), r.chat().get(4));
        assertEquals(new ActivityResponse.ChatPoint("2026-09-06", 0, 2, 3), r.chat().get(5)); // the assistant's reply is not counted
    }

    @Test
    void cropDoctorScansPerBucket() {
        ActivityResponse r = service.activity(FROM, TO, Granularity.DAY);
        assertEquals(2, r.cropDoctor().get(5).scans());
        assertEquals(1, r.cropDoctor().get(30).scans());
    }

    // ---- snapshot ------------------------------------------------------------------------------------------

    @Test
    void snapshotCountsAccountsActivePushDevicesAndCartsWithItems() {
        SnapshotResponse s = service.snapshot();
        assertEquals(new SnapshotResponse.Accounts(8, 1, 7), s.accounts());
        assertEquals(new SnapshotResponse.PushDevices(3, 2, 2, 1), s.pushDevices()); // the inactive token is not counted
        assertEquals(new SnapshotResponse.Carts(2, 6, 1), s.carts());                // the empty cart is not counted; only u1's was touched since 30 Sep
    }
}
