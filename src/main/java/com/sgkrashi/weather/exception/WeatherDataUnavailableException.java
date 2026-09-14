package com.sgkrashi.weather.exception;

/**
 * Thrown by {@code PublicWeatherService} when the underlying {@code
 * WeatherService.fetchWeather(lat, lon)} call comes back {@code
 * Optional.empty()} — that method swallows its own failures (by design,
 * for its AI-grounding callers), so this is where a *user-facing* request
 * turns that into an actual error response ({@code GlobalExceptionHandler}
 * maps it to 503), rather than silently returning nothing.
 */
public class WeatherDataUnavailableException extends RuntimeException {

    public WeatherDataUnavailableException(String message) {
        super(message);
    }
}
