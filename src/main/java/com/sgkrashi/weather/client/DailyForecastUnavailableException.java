package com.sgkrashi.weather.client;

/** Unchecked — {@code DailyForecastServiceImpl} always catches this and degrades to an empty series, never lets it fail the weather response. */
public class DailyForecastUnavailableException extends RuntimeException {

    public DailyForecastUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public DailyForecastUnavailableException(String message) {
        super(message);
    }
}
