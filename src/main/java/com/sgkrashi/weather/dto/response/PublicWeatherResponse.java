package com.sgkrashi.weather.dto.response;

import java.util.List;

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
 *
 * <p>{@code daily} was added later and is additive-only (existing fields
 * unchanged, so older web/mobile builds keep working): the 7-day forecast
 * series for the sparkline. Empty — never null — when that separate
 * Open-Meteo call failed; the rest of the response is still valid then.
 */
public record PublicWeatherResponse(
        double temperatureCelsius,
        double humidityPercent,
        double recentRainfallMm,
        String forecastSummary,
        double forecastMinTempCelsius,
        double forecastPrecipitationNext24hMm,
        List<DailyForecastPointResponse> daily
) {
}
