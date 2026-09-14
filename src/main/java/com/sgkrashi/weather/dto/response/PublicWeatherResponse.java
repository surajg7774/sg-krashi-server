package com.sgkrashi.weather.dto.response;

/**
 * The user-facing counterpart to {@code WeatherSnapshot}
 * ({@code com.sgkrashi.ai.weather.dto}) — same underlying fields, but that
 * type is documented as never shown raw to a user (AI-grounding-context
 * only), so this is a deliberately separate response type rather than
 * reusing it directly. No location label here: the caller already knows
 * the label (either "Your current location" for a geolocated request, or
 * the name/state/country from whichever {@link GeocodingResultResponse}
 * they searched and picked) — round-tripping it through this response
 * would just be an extra thing to keep in sync for no benefit.
 */
public record PublicWeatherResponse(
        double temperatureCelsius,
        double humidityPercent,
        double recentRainfallMm,
        String forecastSummary,
        double forecastMinTempCelsius,
        double forecastPrecipitationNext24hMm
) {
}
