package com.sgkrashi.advisory.rule;

import com.sgkrashi.ai.weather.dto.WeatherSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simple, explicitly-documented rule-of-thumb thresholds — general weather
 * guidance a farmer could work out themselves from a forecast, not a
 * personalized agronomic model (no crop type, soil, or growth-stage input).
 * Each rule is a small pure function of a {@link WeatherSnapshot}, so each
 * is independently testable with a constructed snapshot — no real weather
 * event, network call, or scheduled job run required to verify one fires.
 *
 * <p>Thresholds are deliberately simple round numbers, not derived from any
 * agronomic dataset: 40°C for "extreme heat", 4°C forecast low for "frost
 * risk" (most sensitive crops show damage at or just above literal 0°C
 * frost, so 4°C is a warn-ahead buffer, not the frost point itself), and
 * >1mm forecast rain tomorrow for "hold off on spraying" (matches the same
 * >1mm threshold {@code WeatherApiClient.forecastSummary} already uses to
 * call rain "significant").
 */
public final class AdvisoryRules {

    private static final double RAIN_THRESHOLD_MM = 1.0;
    private static final double EXTREME_HEAT_THRESHOLD_C = 40.0;
    private static final double FROST_RISK_THRESHOLD_C = 4.0;

    private AdvisoryRules() {
    }

    public static Optional<Advisory> rainNext24h(WeatherSnapshot snapshot) {
        if (snapshot.forecastPrecipitationNext24hMm() > RAIN_THRESHOLD_MM) {
            return Optional.of(new Advisory(
                    "Rain expected — hold off on spraying",
                    "Rain is forecast in the next 24 hours (~%.1fmm). Spraying pesticide or fertilizer today risks it washing off before it takes effect — consider waiting until after the rain clears."
                            .formatted(snapshot.forecastPrecipitationNext24hMm())));
        }
        return Optional.empty();
    }

    public static Optional<Advisory> extremeHeat(WeatherSnapshot snapshot) {
        if (snapshot.temperatureCelsius() > EXTREME_HEAT_THRESHOLD_C) {
            return Optional.of(new Advisory(
                    "Extreme heat — increase irrigation",
                    "Current temperature is %.1f°C, well above normal. Crops lose moisture faster in this heat — consider increasing your irrigation frequency, ideally watering early morning or evening to reduce evaporation loss."
                            .formatted(snapshot.temperatureCelsius())));
        }
        return Optional.empty();
    }

    public static Optional<Advisory> frostRisk(WeatherSnapshot snapshot) {
        if (snapshot.forecastMinTempCelsius() < FROST_RISK_THRESHOLD_C) {
            return Optional.of(new Advisory(
                    "Frost risk tonight — protect sensitive crops",
                    "Tomorrow's forecast low is %.1f°C, cold enough to risk frost damage. Consider covering frost-sensitive crops or seedlings overnight."
                            .formatted(snapshot.forecastMinTempCelsius())));
        }
        return Optional.empty();
    }

    /** Runs every rule and returns whichever ones actually triggered — empty if the weather isn't noteworthy today. */
    public static List<Advisory> evaluateAll(WeatherSnapshot snapshot) {
        List<Advisory> triggered = new ArrayList<>();
        rainNext24h(snapshot).ifPresent(triggered::add);
        extremeHeat(snapshot).ifPresent(triggered::add);
        frostRisk(snapshot).ifPresent(triggered::add);
        return triggered;
    }
}
