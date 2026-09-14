package com.sgkrashi.weather.service;

import com.sgkrashi.weather.dto.response.GeocodingResultResponse;
import com.sgkrashi.weather.dto.response.PublicWeatherResponse;

import java.util.List;

/** User-facing weather + location-search — see {@code WeatherController}, the only caller. */
public interface PublicWeatherService {

    /**
     * @throws com.sgkrashi.weather.exception.WeatherDataUnavailableException if the upstream weather call failed.
     */
    PublicWeatherResponse getWeather(double latitude, double longitude);

    List<GeocodingResultResponse> searchLocations(String query);
}
