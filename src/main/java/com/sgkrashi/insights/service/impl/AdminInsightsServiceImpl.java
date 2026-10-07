package com.sgkrashi.insights.service.impl;

import com.sgkrashi.common.exception.ValidationException;
import com.sgkrashi.insights.dto.response.InsightsResponses.ActivityResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.BookingsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.FulfilmentResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.OrdersResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SignupsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SnapshotResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.UsageResponse;
import com.sgkrashi.insights.repository.InsightsQueryRepository;
import com.sgkrashi.insights.repository.InsightsQueryRepository.BookingRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.ChatRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.CountRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.FulfilmentRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.OrderRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.RepeatRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.SignupRow;
import com.sgkrashi.insights.service.AdminInsightsService;
import com.sgkrashi.insights.util.Granularity;
import com.sgkrashi.usage.UsageFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns the repository's grouped rows into complete, zero-filled series (a day with no signups is a real
 * zero, not a gap) and computes the ratios. Nothing here writes; every method is a read-only transaction.
 */
@Service
@Transactional(readOnly = true)
public class AdminInsightsServiceImpl implements AdminInsightsService {

    static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");
    /** Keeps one request cheap: at most a year of days (or weeks/months), whatever the granularity. */
    static final int MAX_RANGE_DAYS = 366;

    private final InsightsQueryRepository repository;
    private final Clock clock;

    @Autowired
    public AdminInsightsServiceImpl(InsightsQueryRepository repository) {
        this(repository, Clock.system(ADMIN_ZONE));
    }

    AdminInsightsServiceImpl(InsightsQueryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ---- signups -------------------------------------------------------------------------------------------

    @Override
    public SignupsResponse signups(LocalDate from, LocalDate to, Granularity g) {
        validate(from, to);
        Map<String, SignupRow> rows = byBucket(repository.signups(g, from, to), SignupRow::bucket);
        List<SignupsResponse.SignupPoint> points = new ArrayList<>();
        long total = 0, email = 0, google = 0, deleted = 0;
        for (LocalDate b : g.bucketsBetween(from, to)) {
            SignupRow r = rows.get(b.toString());
            long t = r == null ? 0 : r.total(), e = r == null ? 0 : r.email(), go = r == null ? 0 : r.google(), d = r == null ? 0 : r.deleted();
            points.add(new SignupsResponse.SignupPoint(b.toString(), t, e, go, d));
            total += t;
            email += e;
            google += go;
            deleted += d;
        }
        return new SignupsResponse(g.label(), points, new SignupsResponse.SignupTotals(total, email, google, deleted));
    }

    // ---- orders --------------------------------------------------------------------------------------------

    @Override
    public OrdersResponse orders(LocalDate from, LocalDate to, Granularity g) {
        validate(from, to);
        Map<String, OrderRow> rows = byBucket(repository.orders(g, from, to), OrderRow::bucket);
        List<OrdersResponse.OrderPoint> points = new ArrayList<>();
        long created = 0, paid = 0, failed = 0, pending = 0;
        BigDecimal paidAmount = BigDecimal.ZERO;
        for (LocalDate b : g.bucketsBetween(from, to)) {
            OrderRow r = rows.get(b.toString());
            long c = r == null ? 0 : r.created(), p = r == null ? 0 : r.paid(), f = r == null ? 0 : r.failed(), pe = r == null ? 0 : r.pending();
            points.add(new OrdersResponse.OrderPoint(b.toString(), c, p, f, pe));
            created += c;
            paid += p;
            failed += f;
            pending += pe;
            if (r != null) paidAmount = paidAmount.add(r.paidAmount());
        }
        Double rate = created == 0 ? null : round(paid / (double) created, 4);
        BigDecimal aov = paid == 0 ? null : paidAmount.divide(BigDecimal.valueOf(paid), 2, RoundingMode.HALF_UP);
        RepeatRow repeat = repository.repeatCustomers();
        Double repeatRate = repeat.customersWhoPaid() == 0 ? null : round(repeat.repeatCustomers() / (double) repeat.customersWhoPaid(), 4);
        return new OrdersResponse(g.label(), points, new OrdersResponse.OrderTotals(created, paid, failed, pending, rate, aov),
                new OrdersResponse.RepeatCustomers(repeat.customersWhoPaid(), repeat.repeatCustomers(), repeatRate));
    }

    @Override
    public FulfilmentResponse fulfilment(LocalDate from, LocalDate to) {
        validate(from, to);
        List<FulfilmentRow> rows = repository.fulfilment(from, to);
        return new FulfilmentResponse(List.of(
                stage("placed-to-paid", "Order placed to payment confirmed", rows, FulfilmentRow::placedToPaid),
                stage("paid-to-shipped", "Payment confirmed to shipped", rows, FulfilmentRow::paidToShipped),
                stage("shipped-to-delivered", "Shipped to delivered", rows, FulfilmentRow::shippedToDelivered),
                stage("paid-to-delivered", "Payment confirmed to delivered (all delivered orders)", rows, FulfilmentRow::paidToDelivered)));
    }

    private static FulfilmentResponse.Stage stage(String key, String label, List<FulfilmentRow> rows, Function<FulfilmentRow, Long> seconds) {
        List<Long> values = new ArrayList<>();
        for (FulfilmentRow row : rows) {
            Long s = seconds.apply(row);
            if (s != null && s >= 0) values.add(s); // a negative gap would be a data error, not a duration
        }
        if (values.isEmpty()) return new FulfilmentResponse.Stage(key, label, 0, null, null);
        Collections.sort(values);
        return new FulfilmentResponse.Stage(key, label, values.size(), Math.round(median(values)), Math.round(average(values)));
    }

    static double median(List<Long> sorted) {
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    static double average(List<Long> values) {
        long sum = 0;
        for (long v : values) sum += v;
        return sum / (double) values.size();
    }

    // ---- bookings ------------------------------------------------------------------------------------------

    @Override
    public BookingsResponse bookings(LocalDate from, LocalDate to, Granularity g) {
        validate(from, to);
        Map<String, BookingRow> rows = byBucket(repository.bookings(g, from, to), BookingRow::bucket);
        List<BookingsResponse.BookingPoint> points = new ArrayList<>();
        long[] t = new long[7];
        BigDecimal value = BigDecimal.ZERO;
        for (LocalDate b : g.bucketsBetween(from, to)) {
            BookingRow r = rows.get(b.toString());
            long[] v = r == null ? new long[7]
                    : new long[]{r.created(), r.pending(), r.confirmed(), r.completed(), r.cancelled(), r.equipment(), r.stay()};
            points.add(new BookingsResponse.BookingPoint(b.toString(), v[0], v[1], v[2], v[3], v[4], v[5], v[6]));
            for (int i = 0; i < 7; i++) t[i] += v[i];
            if (r != null) value = value.add(r.confirmedValue());
        }
        return new BookingsResponse(g.label(), points, new BookingsResponse.BookingTotals(t[0], t[1], t[2], t[3], t[4], t[5], t[6], value));
    }

    // ---- activity ------------------------------------------------------------------------------------------

    @Override
    public ActivityResponse activity(LocalDate from, LocalDate to, Granularity g) {
        validate(from, to);
        Map<String, CountRow> active = byBucket(repository.activeUsers(g, from, to), CountRow::bucket);
        Map<String, ChatRow> chat = byBucket(repository.chat(g, from, to), ChatRow::bucket);
        Map<String, CountRow> scans = byBucket(repository.cropScans(g, from, to), CountRow::bucket);
        List<ActivityResponse.ActivePoint> activePoints = new ArrayList<>();
        List<ActivityResponse.ChatPoint> chatPoints = new ArrayList<>();
        List<ActivityResponse.ScanPoint> scanPoints = new ArrayList<>();
        for (LocalDate b : g.bucketsBetween(from, to)) {
            String key = b.toString();
            activePoints.add(new ActivityResponse.ActivePoint(key, active.containsKey(key) ? active.get(key).count() : 0));
            ChatRow c = chat.get(key);
            chatPoints.add(new ActivityResponse.ChatPoint(key, c == null ? 0 : c.guestSessions(), c == null ? 0 : c.loggedInSessions(),
                    c == null ? 0 : c.messagesFromPeople()));
            scanPoints.add(new ActivityResponse.ScanPoint(key, scans.containsKey(key) ? scans.get(key).count() : 0));
        }
        LocalDate today = LocalDate.now(clock);
        ActivityResponse.ActiveNow now = new ActivityResponse.ActiveNow(
                repository.activeUsersBetween(today.minusDays(6), today), repository.activeUsersBetween(today.minusDays(29), today));
        return new ActivityResponse(g.label(), activePoints, chatPoints, scanPoints, now);
    }

    // ---- feature usage -------------------------------------------------------------------------------------

    @Override
    public UsageResponse usage(LocalDate from, LocalDate to, Granularity g) {
        validate(from, to);
        String since = repository.usageCountingSince();
        Map<String, Long> totals = new java.util.LinkedHashMap<>();
        for (UsageFeature f : UsageFeature.values()) totals.put(f.key(), 0L);
        if (since == null) return new UsageResponse(g.label(), null, List.of(), totals);

        Map<String, Map<String, Long>> byBucket = new HashMap<>();
        for (InsightsQueryRepository.UsageRow row : repository.usage(g, from, to)) {
            byBucket.computeIfAbsent(row.bucket(), k -> new HashMap<>()).merge(row.feature(), row.count(), Long::sum);
        }
        // Days before counting started are not zeros, they are unknown, so those buckets are left out.
        LocalDate firstBucket = g.bucketStart(LocalDate.parse(since));
        List<UsageResponse.UsagePoint> points = new ArrayList<>();
        for (LocalDate b : g.bucketsBetween(from, to)) {
            if (b.isBefore(firstBucket)) continue;
            Map<String, Long> counts = new java.util.LinkedHashMap<>();
            Map<String, Long> row = byBucket.getOrDefault(b.toString(), Map.of());
            for (UsageFeature f : UsageFeature.values()) {
                long n = row.getOrDefault(f.key(), 0L);
                counts.put(f.key(), n);
                totals.merge(f.key(), n, Long::sum);
            }
            points.add(new UsageResponse.UsagePoint(b.toString(), counts));
        }
        return new UsageResponse(g.label(), since, points, totals);
    }

    // ---- snapshot ------------------------------------------------------------------------------------------

    @Override
    public SnapshotResponse snapshot() {
        long[] accounts = repository.accountTotals();
        long[] push = repository.pushDevices();
        long[] carts = repository.carts(LocalDate.now(clock).minusDays(6));
        return new SnapshotResponse(
                new SnapshotResponse.Accounts(accounts[0], accounts[1], accounts[0] - accounts[1]),
                new SnapshotResponse.PushDevices(push[0], push[1], push[2], push[3]),
                new SnapshotResponse.Carts(carts[0], carts[1], carts[2]));
    }

    // ---- shared --------------------------------------------------------------------------------------------

    void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new ValidationException("from and to are required");
        if (to.isBefore(from)) throw new ValidationException("to must not be before from");
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ValidationException("The date range can be at most " + MAX_RANGE_DAYS + " days");
        }
    }

    private static <T> Map<String, T> byBucket(List<T> rows, Function<T, String> key) {
        Map<String, T> map = new HashMap<>();
        for (T row : rows) map.put(key.apply(row), row);
        return map;
    }

    private static double round(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
