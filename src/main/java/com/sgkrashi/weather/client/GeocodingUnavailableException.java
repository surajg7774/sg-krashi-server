package com.sgkrashi.weather.client;

/** Unchecked — mirrors {@code WeatherUnavailableException}'s role for {@code WeatherApiClient}: callers always catch or let it propagate to {@code GlobalExceptionHandler} rather than failing in an unhandled way. */
public class GeocodingUnavailableException extends RuntimeException {

    public GeocodingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
