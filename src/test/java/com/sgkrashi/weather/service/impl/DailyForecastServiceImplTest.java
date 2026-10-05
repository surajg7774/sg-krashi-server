package com.sgkrashi.weather.service.impl;

import com.sgkrashi.weather.client.DailyForecastApiClient;
import com.sgkrashi.weather.client.DailyForecastUnavailableException;
import com.sgkrashi.weather.dto.response.DailyForecastPointResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyForecastServiceImplTest {

    private static final List<DailyForecastPointResponse> FORECAST =
            List.of(new DailyForecastPointResponse(LocalDate.of(2026, 10, 5), 32.2, 25.5, 0.3));

    private final MutableClock clock = new MutableClock();
    private DailyForecastApiClient client;
    private DailyForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        client = mock(DailyForecastApiClient.class);
        service = new DailyForecastServiceImpl(client, clock);
    }

    @Test
    void secondCallForTheSameLocationIsServedFromCache() {
        when(client.fetch(26.14, 91.74)).thenReturn(FORECAST);

        assertEquals(FORECAST, service.getDailyForecast(26.14, 91.74));
        assertEquals(FORECAST, service.getDailyForecast(26.14, 91.74));

        verify(client, times(1)).fetch(26.14, 91.74);
    }

    @Test
    void nearbyCoordinatesShareOneUpstreamCall() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(FORECAST);

        service.getDailyForecast(26.1401, 91.7399);
        service.getDailyForecast(26.1399, 91.7401);

        verify(client, times(1)).fetch(anyDouble(), anyDouble());
    }

    @Test
    void refetchesOnceTheTtlHasPassed() {
        when(client.fetch(26.14, 91.74)).thenReturn(FORECAST);

        service.getDailyForecast(26.14, 91.74);
        clock.advance(DailyForecastServiceImpl.CACHE_TTL.plusSeconds(1));
        service.getDailyForecast(26.14, 91.74);

        verify(client, times(2)).fetch(26.14, 91.74);
    }

    @Test
    void failureYieldsAnEmptySeriesAndIsNotCached() {
        when(client.fetch(26.14, 91.74))
                .thenThrow(new DailyForecastUnavailableException("down"))
                .thenReturn(FORECAST);

        assertEquals(List.of(), service.getDailyForecast(26.14, 91.74));
        assertEquals(FORECAST, service.getDailyForecast(26.14, 91.74));

        verify(client, times(2)).fetch(26.14, 91.74);
    }

    @Test
    void anExpiredEntryIsNotServedWhenTheRefreshFails() {
        when(client.fetch(26.14, 91.74))
                .thenReturn(FORECAST)
                .thenThrow(new DailyForecastUnavailableException("down"));

        service.getDailyForecast(26.14, 91.74);
        clock.advance(DailyForecastServiceImpl.CACHE_TTL.plusSeconds(1));

        assertEquals(List.of(), service.getDailyForecast(26.14, 91.74));
    }

    @Test
    void cacheStaysBoundedWhenCoordinatesAreUserControlled() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(FORECAST);

        for (int i = 0; i <= DailyForecastServiceImpl.MAX_ENTRIES + 50; i++) {
            service.getDailyForecast(i * 0.01, 10.0);
            clock.advance(Duration.ofMillis(1));
        }

        assertTrue(service.cacheSize() <= DailyForecastServiceImpl.MAX_ENTRIES,
                "cache grew to " + service.cacheSize());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
