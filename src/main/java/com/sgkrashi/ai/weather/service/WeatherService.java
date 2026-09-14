package com.sgkrashi.ai.weather.service;

import com.sgkrashi.ai.weather.dto.WeatherSnapshot;

import java.util.Optional;

/**
 * Grounding-context source for AI Crop Doctor and the chat assistant —
 * genuinely analogous to {@code RetrievalService}/{@code
 * PlatformKnowledgeService}: the backend decides when weather is relevant
 * and fetches it itself, then hands the model plain text. Neither {@code
 * GeminiAnalysisProvider} nor {@code GeminiChatProvider} is ever given a
 * tool/function-calling capability to fetch weather on its own — this
 * interface, called only from the backend's own request-handling code, is
 * the entire seam.
 *
 * <p>Backed by a direct REST call to Open-Meteo ({@code WeatherApiClient}),
 * not a real weather MCP server connection — see {@code WeatherApiClient}'s
 * Javadoc for the research and reasoning. Callers here never know that
 * either way: {@link #fetchCurrentWeather()} returns {@link Optional#empty()}
 * on any failure (network, timeout, malformed response), logged but never
 * thrown, so a weather outage can only ever mean "proceed without weather
 * grounding" — never a failed scan or a failed chat reply.
 *
 * <p>No location parameter on {@link #fetchCurrentWeather()}, deliberately:
 * per-user/per-listing location and geocoding were explicitly out of scope
 * when that method was written (the whole platform was one farm then), so a
 * parameter nobody would ever vary would just have been a confusing,
 * dishonest API surface. {@link #fetchWeather(double, double)} below is
 * exactly the extension point that Javadoc anticipated — added for the
 * weather advisory job (Facility Feature #3), once farmers actually have
 * their own geographically diverse locations via {@code FarmerProfile}.
 */
public interface WeatherService {

    Optional<WeatherSnapshot> fetchCurrentWeather();

    /**
     * Same contract as {@link #fetchCurrentWeather()} (never throws, {@link
     * Optional#empty()} on any failure) but for an arbitrary location —
     * used by the daily weather advisory job, one call per opted-in
     * farmer's own coordinates. Deliberately uncached (unlike {@link
     * #fetchCurrentWeather()}'s single-slot 45-minute cache, which assumes
     * one fixed location) — a daily scheduled job across many distinct
     * farmer locations isn't a hot path worth caching.
     */
    Optional<WeatherSnapshot> fetchWeather(double latitude, double longitude);
}
