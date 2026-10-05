package com.sgkrashi.weather.service;

import com.sgkrashi.weather.dto.response.DailyForecastPointResponse;

import java.util.List;

/** The 7-day forecast series behind the weather sparkline — see {@code PublicWeatherServiceImpl}, the only caller. */
public interface DailyForecastService {

    /** Never throws: an Open-Meteo failure yields an empty list, so the rest of the weather response is unaffected. */
    List<DailyForecastPointResponse> getDailyForecast(double latitude, double longitude);
}
