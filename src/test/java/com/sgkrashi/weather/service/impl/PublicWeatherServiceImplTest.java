package com.sgkrashi.weather.service.impl;

import com.sgkrashi.ai.weather.dto.WeatherSnapshot;
import com.sgkrashi.ai.weather.service.WeatherService;
import com.sgkrashi.weather.client.GeocodingApiClient;
import com.sgkrashi.weather.dto.response.DailyForecastPointResponse;
import com.sgkrashi.weather.dto.response.PublicWeatherResponse;
import com.sgkrashi.weather.exception.WeatherDataUnavailableException;
import com.sgkrashi.weather.service.DailyForecastService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicWeatherServiceImplTest {

    private static final WeatherSnapshot SNAPSHOT = new WeatherSnapshot(29.5, 71.0, 4.2, "Rain expected tomorrow (~7.5mm)", 22.3, 7.5);
    private static final List<DailyForecastPointResponse> DAILY =
            List.of(new DailyForecastPointResponse(LocalDate.of(2026, 10, 5), 32.2, 25.5, 0.3));

    private final WeatherService weatherService = mock(WeatherService.class);
    private final DailyForecastService dailyForecastService = mock(DailyForecastService.class);
    private final PublicWeatherServiceImpl service =
            new PublicWeatherServiceImpl(weatherService, mock(GeocodingApiClient.class), dailyForecastService);

    @Test
    void addsTheDailySeriesAndLeavesEveryExistingFieldUnchanged() {
        when(weatherService.fetchWeather(26.14, 91.74)).thenReturn(Optional.of(SNAPSHOT));
        when(dailyForecastService.getDailyForecast(26.14, 91.74)).thenReturn(DAILY);

        PublicWeatherResponse response = service.getWeather(26.14, 91.74);

        assertEquals(29.5, response.temperatureCelsius());
        assertEquals(71.0, response.humidityPercent());
        assertEquals(4.2, response.recentRainfallMm());
        assertEquals("Rain expected tomorrow (~7.5mm)", response.forecastSummary());
        assertEquals(22.3, response.forecastMinTempCelsius());
        assertEquals(7.5, response.forecastPrecipitationNext24hMm());
        assertEquals(DAILY, response.daily());
    }

    @Test
    void anEmptyDailySeriesDoesNotFailTheResponse() {
        when(weatherService.fetchWeather(26.14, 91.74)).thenReturn(Optional.of(SNAPSHOT));
        when(dailyForecastService.getDailyForecast(26.14, 91.74)).thenReturn(List.of());

        PublicWeatherResponse response = service.getWeather(26.14, 91.74);

        assertEquals(29.5, response.temperatureCelsius());
        assertEquals(List.of(), response.daily());
    }

    @Test
    void stillFailsWhenTheCurrentWeatherItselfIsUnavailable() {
        when(weatherService.fetchWeather(26.14, 91.74)).thenReturn(Optional.empty());

        assertThrows(WeatherDataUnavailableException.class, () -> service.getWeather(26.14, 91.74));
    }
}
