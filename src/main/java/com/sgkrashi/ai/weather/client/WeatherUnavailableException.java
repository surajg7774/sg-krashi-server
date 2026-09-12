package com.sgkrashi.ai.weather.client;

/** Unchecked — {@code WeatherServiceImpl} always catches this and degrades to no weather grounding, never lets it fail an AI Crop Doctor scan or chat reply. */
public class WeatherUnavailableException extends RuntimeException {

    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
