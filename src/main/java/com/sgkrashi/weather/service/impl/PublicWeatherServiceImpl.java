package com.sgkrashi.weather.service.impl;

import com.sgkrashi.ai.weather.dto.WeatherSnapshot;
import com.sgkrashi.ai.weather.service.WeatherService;
import com.sgkrashi.weather.client.GeocodingApiClient;
import com.sgkrashi.weather.dto.response.GeocodingResultResponse;
import com.sgkrashi.weather.dto.response.PublicWeatherResponse;
import com.sgkrashi.weather.exception.WeatherDataUnavailableException;
import com.sgkrashi.weather.service.PublicWeatherService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PublicWeatherServiceImpl implements PublicWeatherService {

    private final WeatherService weatherService;
    private final GeocodingApiClient geocodingApiClient;

    public PublicWeatherServiceImpl(WeatherService weatherService, GeocodingApiClient geocodingApiClient) {
        this.weatherService = weatherService;
        this.geocodingApiClient = geocodingApiClient;
    }

    @Override
    public PublicWeatherResponse getWeather(double latitude, double longitude) {
        Optional<WeatherSnapshot> snapshot = weatherService.fetchWeather(latitude, longitude);
        WeatherSnapshot s = snapshot.orElseThrow(() ->
                new WeatherDataUnavailableException("Weather is temporarily unavailable for this location"));
        return new PublicWeatherResponse(
                s.temperatureCelsius(),
                s.humidityPercent(),
                s.recentRainfallMm(),
                s.forecastSummary(),
                s.forecastMinTempCelsius(),
                s.forecastPrecipitationNext24hMm());
    }

    @Override
    public List<GeocodingResultResponse> searchLocations(String query) {
        // GeocodingUnavailableException propagates uncaught to
        // GlobalExceptionHandler (503) — same "don't swallow a
        // user-facing failure" reasoning as getWeather above.
        return geocodingApiClient.search(query);
    }
}
