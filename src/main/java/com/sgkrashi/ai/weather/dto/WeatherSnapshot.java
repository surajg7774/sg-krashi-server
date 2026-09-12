package com.sgkrashi.ai.weather.dto;

/** A point-in-time weather reading for the farm's location — grounding context for AI Crop Doctor and the chat assistant, never shown raw to a user without the model's own framing. */
public record WeatherSnapshot(
        double temperatureCelsius,
        double humidityPercent,
        double recentRainfallMm,
        String forecastSummary
) {
}
