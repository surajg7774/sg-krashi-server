package com.sgkrashi.insights.service.impl;

import com.sgkrashi.insights.dto.response.InsightsResponses.UsageResponse;
import com.sgkrashi.insights.repository.InsightsQueryRepository;
import com.sgkrashi.insights.util.Granularity;
import com.sgkrashi.usage.UsageDailyRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the usage counters' real SQL (the additive upsert and the Insights usage query) against a real MySQL that
 * already has migration V41. Off unless {@code -Dinsights.db.url=jdbc:mysql://localhost:PORT/DB} is given; it
 * deletes every row of {@code usage_daily}, so it refuses any host other than localhost.
 */
@EnabledIfSystemProperty(named = "insights.db.url", matches = ".+")
class UsageInsightsDbTest {

    private static JdbcTemplate jdbc;
    private static UsageDailyRepository repository;
    private static AdminInsightsServiceImpl service;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void connect() {
        String url = System.getProperty("insights.db.url");
        if (!(url.contains("//localhost") || url.contains("//127.0.0.1"))) {
            throw new IllegalStateException("Refusing to run: this test deletes rows and may only target a local throwaway database");
        }
        dataSource = new DriverManagerDataSource(url, System.getProperty("insights.db.user", "root"), System.getProperty("insights.db.password", ""));
        jdbc = new JdbcTemplate(dataSource);
        repository = new UsageDailyRepository(jdbc);
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        service = new AdminInsightsServiceImpl(new InsightsQueryRepository(new NamedParameterJdbcTemplate(jdbc)), clock);
    }

    @BeforeEach
    void emptyTable() {
        jdbc.update("DELETE FROM usage_daily");
    }

    private static long count(String day, String feature) {
        List<Long> rows = jdbc.queryForList("SELECT `count` FROM usage_daily WHERE day = ? AND feature = ?", Long.class, day, feature);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    @Test
    void theTableHoldsOnlyDayFeatureAndCountSoItCannotSayWhoDidAnything() {
        List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'usage_daily' ORDER BY ordinal_position", String.class);
        assertEquals(List.of("day", "feature", "count"), columns);
    }

    @Test
    void addingIsAdditiveAcrossCallsAndSeparatePerDayAndFeature() {
        repository.add(LocalDate.of(2026, 10, 5), "product_detail", 3);
        repository.add(LocalDate.of(2026, 10, 5), "product_detail", 4);
        repository.add(LocalDate.of(2026, 10, 5), "weather", 1);
        repository.add(LocalDate.of(2026, 10, 6), "product_detail", 10);
        assertEquals(7, count("2026-10-05", "product_detail"));
        assertEquals(1, count("2026-10-05", "weather"));
        assertEquals(10, count("2026-10-06", "product_detail"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM usage_daily", Long.class), "one row per (day, feature)");
    }

    @Test
    void theUpsertIsFreeOfWarnings() {
        var single = new org.springframework.jdbc.datasource.SingleConnectionDataSource(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword(), true);
        var one = new JdbcTemplate(single);
        new UsageDailyRepository(one).add(LocalDate.of(2026, 10, 5), "mandi_prices", 1);
        new UsageDailyRepository(one).add(LocalDate.of(2026, 10, 5), "mandi_prices", 1); // the "duplicate key" branch
        assertEquals(0, one.queryForList("SHOW WARNINGS").size(), "no deprecation or truncation warnings");
        single.destroy();
    }

    @Test
    void parallelWritersNeverLoseACount() throws Exception {
        int threads = 16, perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) repository.add(LocalDate.of(2026, 10, 6), "add_to_cart", 1);
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS));
        assertEquals(threads * perThread, count("2026-10-06", "add_to_cart"));
    }

    @Test
    void usagePerDayIsZeroFilledFromTheFirstCountedDayOnly() {
        repository.add(LocalDate.of(2026, 10, 4), "product_detail", 5);
        repository.add(LocalDate.of(2026, 10, 4), "search_no_results", 2);
        repository.add(LocalDate.of(2026, 10, 6), "product_detail", 8);
        repository.add(LocalDate.of(2026, 10, 6), "search_with_results", 6);

        UsageResponse r = service.usage(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 6), Granularity.DAY);

        assertEquals("2026-10-04", r.countingSince());
        assertEquals(3, r.points().size(), "1 Sep to 3 Oct are before counting started: unknown, not zero, so they are left out");
        assertEquals("2026-10-04", r.points().get(0).bucket());
        assertEquals(5L, r.points().get(0).counts().get("product_detail"));
        assertEquals(0L, r.points().get(1).counts().get("product_detail")); // 5 Oct: counting was on and nothing happened
        assertEquals(8L, r.points().get(2).counts().get("product_detail"));
        assertEquals(11, r.points().get(0).counts().size(), "every feature is present in every point");
        assertEquals(13L, r.totals().get("product_detail"));
        assertEquals(2L, r.totals().get("search_no_results"));
        assertEquals(6L, r.totals().get("search_with_results"));
        assertEquals(0L, r.totals().get("guest_crop_scan"));
    }

    @Test
    void usagePerWeekAndMonthRegroupTheDays() {
        repository.add(LocalDate.of(2026, 9, 30), "weather", 1); // Wednesday, week of 28 Sep, month of Sep
        repository.add(LocalDate.of(2026, 10, 1), "weather", 2);  // Thursday, same week, month of Oct
        repository.add(LocalDate.of(2026, 10, 6), "weather", 4);  // Tuesday, week of 5 Oct

        UsageResponse weeks = service.usage(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 6), Granularity.WEEK);
        assertEquals(List.of("2026-09-28", "2026-10-05"), weeks.points().stream().map(UsageResponse.UsagePoint::bucket).toList());
        assertEquals(3L, weeks.points().get(0).counts().get("weather"));
        assertEquals(4L, weeks.points().get(1).counts().get("weather"));

        UsageResponse months = service.usage(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 6), Granularity.MONTH);
        assertEquals(List.of("2026-09-01", "2026-10-01"), months.points().stream().map(UsageResponse.UsagePoint::bucket).toList());
        assertEquals(1L, months.points().get(0).counts().get("weather"));
        assertEquals(6L, months.points().get(1).counts().get("weather"));
    }

    @Test
    void anEmptyTableMeansCountingHasNotStartedYet() {
        UsageResponse r = service.usage(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 6), Granularity.DAY);
        assertNull(r.countingSince());
        assertTrue(r.points().isEmpty());
        assertTrue(r.totals().values().stream().allMatch(v -> v == 0));
    }

    @Test
    void aRangeEntirelyBeforeCountingStartedHasNoPoints() {
        repository.add(LocalDate.of(2026, 10, 4), "schemes", 3);
        UsageResponse r = service.usage(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Granularity.DAY);
        assertEquals("2026-10-04", r.countingSince());
        assertTrue(r.points().isEmpty());
        assertEquals(0L, r.totals().get("schemes"));
    }
}
