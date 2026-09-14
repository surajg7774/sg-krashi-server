package com.sgkrashi.ai.weather.dto;

/**
 * A point-in-time weather reading — grounding context for AI Crop Doctor and
 * the chat assistant (never shown raw to a user without the model's own
 * framing), and structured input for {@code AdvisoryRules} (the weather
 * advisory job, Facility Feature #3) to threshold on directly rather than
 * string-matching {@code forecastSummary}.
 *
 * <p>{@code forecastMinTempCelsius}/{@code forecastPrecipitationNext24hMm}
 * come from the same Open-Meteo {@code daily} response block {@code
 * forecastSummary} is already derived from — not a second API call.
 */
public record WeatherSnapshot(
        double temperatureCelsius,
        double humidityPercent,
        double recentRainfallMm,
        String forecastSummary,
        double forecastMinTempCelsius,
        double forecastPrecipitationNext24hMm
) {
}
