package com.sgkrashi.insights.service.impl;

import com.sgkrashi.common.exception.ValidationException;
import com.sgkrashi.insights.controller.AdminInsightsController;
import com.sgkrashi.insights.dto.response.InsightsResponses.FulfilmentResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.OrdersResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SignupsResponse;
import com.sgkrashi.insights.repository.InsightsQueryRepository;
import com.sgkrashi.insights.repository.InsightsQueryRepository.FulfilmentRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.OrderRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.RepeatRow;
import com.sgkrashi.insights.repository.InsightsQueryRepository.SignupRow;
import com.sgkrashi.insights.util.Granularity;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The parts of Insights that need no database: validation, bucket filling, ratios and the admin-only guard. */
class AdminInsightsServiceImplTest {

    private final InsightsQueryRepository repository = mock(InsightsQueryRepository.class);
    private final AdminInsightsServiceImpl service = new AdminInsightsServiceImpl(repository,
            Clock.fixed(Instant.parse("2026-10-06T06:30:00Z"), ZoneId.of("Asia/Kolkata")));

    @Test
    void everyBucketInTheRangeIsPresentAndEmptyOnesAreZero() {
        when(repository.signups(any(), any(), any())).thenReturn(List.of(new SignupRow("2026-09-03", 2, 1, 1, 0)));

        SignupsResponse r = service.signups(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), Granularity.DAY);

        assertEquals(5, r.points().size());
        assertEquals(new SignupsResponse.SignupPoint("2026-09-01", 0, 0, 0, 0), r.points().get(0));
        assertEquals(new SignupsResponse.SignupPoint("2026-09-03", 2, 1, 1, 0), r.points().get(2));
        assertEquals(2, r.totals().total());
    }

    @Test
    void ratesAreNullWhenThereIsNothingToDivideBy() {
        when(repository.orders(any(), any(), any())).thenReturn(List.of());
        when(repository.repeatCustomers()).thenReturn(new RepeatRow(0, 0));

        OrdersResponse r = service.orders(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), Granularity.DAY);

        assertNull(r.totals().checkoutToPaidRate());
        assertNull(r.totals().averageOrderValue());
        assertNull(r.repeatCustomers().repeatRate());
    }

    @Test
    void checkoutToPaidRateAndAverageOrderValueComeFromThePaidOrdersOnly() {
        when(repository.orders(any(), any(), any())).thenReturn(List.of(
                new OrderRow("2026-09-01", 4, 2, 1, 1, new BigDecimal("300.00")),
                new OrderRow("2026-09-02", 1, 1, 0, 0, new BigDecimal("50.00"))));
        when(repository.repeatCustomers()).thenReturn(new RepeatRow(3, 1));

        OrdersResponse r = service.orders(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), Granularity.DAY);

        assertEquals(5, r.totals().created());
        assertEquals(3, r.totals().paid());
        assertEquals(0.6, r.totals().checkoutToPaidRate());
        assertEquals(new BigDecimal("116.67"), r.totals().averageOrderValue()); // 350 / 3
        assertEquals(0.3333, r.repeatCustomers().repeatRate());
    }

    @Test
    void medianOfAnEvenAndAnOddNumberOfDurations() {
        assertEquals(2.0, AdminInsightsServiceImpl.median(List.of(1L, 2L, 9L)));
        assertEquals(2.5, AdminInsightsServiceImpl.median(List.of(1L, 2L, 3L, 9L)));
    }

    @Test
    void fulfilmentIgnoresMissingMilestonesAndNegativeGaps() {
        when(repository.fulfilment(any(), any())).thenReturn(List.of(
                new FulfilmentRow(3600L, null, null, null),
                new FulfilmentRow(7200L, null, null, null),
                new FulfilmentRow(-5L, null, null, null)));

        FulfilmentResponse r = service.fulfilment(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertEquals(2, r.stages().get(0).orders());
        assertEquals(5400L, r.stages().get(0).medianSeconds());
        assertEquals(0, r.stages().get(1).orders());
        assertNull(r.stages().get(1).medianSeconds());
    }

    @Test
    void invalidRangesAreRejected() {
        LocalDate d = LocalDate.of(2026, 9, 1);
        assertThrows(ValidationException.class, () -> service.signups(d, d.minusDays(1), Granularity.DAY));
        assertThrows(ValidationException.class, () -> service.signups(null, d, Granularity.DAY));
        assertThrows(ValidationException.class, () -> service.signups(d, d.plusDays(366), Granularity.DAY)); // 367 days
        service.signups(d, d.plusDays(365), Granularity.DAY); // 366 days is the limit and is fine
    }

    @Test
    void groupByAcceptsOnlyDayWeekMonth() {
        assertEquals(Granularity.WEEK, Granularity.parse("WEEK"));
        assertEquals(Granularity.DAY, Granularity.parse(null));
        assertThrows(ValidationException.class, () -> Granularity.parse("year"));
        assertThrows(ValidationException.class, () -> Granularity.parse("day'; DROP TABLE users;--"));
    }

    @Test
    void bucketsStartOnMondayAndOnTheFirstOfTheMonth() {
        assertEquals(LocalDate.of(2026, 8, 31), Granularity.WEEK.bucketStart(LocalDate.of(2026, 9, 1)));  // a Tuesday
        assertEquals(LocalDate.of(2026, 9, 14), Granularity.WEEK.bucketStart(LocalDate.of(2026, 9, 14))); // a Monday
        assertEquals(LocalDate.of(2026, 9, 14), Granularity.WEEK.bucketStart(LocalDate.of(2026, 9, 20))); // a Sunday
        assertEquals(LocalDate.of(2026, 9, 1), Granularity.MONTH.bucketStart(LocalDate.of(2026, 9, 30)));
        assertEquals(List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)),
                Granularity.MONTH.bucketsBetween(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 2)));
    }

    @Test
    void onlyAdminsMayCallTheInsightsEndpoints() {
        PreAuthorize guard = AdminInsightsController.class.getAnnotation(PreAuthorize.class);
        assertTrue(guard != null && guard.value().contains("ADMIN") && guard.value().contains("SUPER_ADMIN"));
    }
}
