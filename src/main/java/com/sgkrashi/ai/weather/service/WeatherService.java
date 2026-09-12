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
 * <p>No location parameter for V1, deliberately: per-user/per-listing
 * location and geocoding are explicitly out of scope for this feature (the
 * whole platform is one farm today), so a parameter nobody would ever vary
 * would just be a confusing, dishonest API surface. The natural extension
 * point — passing a real location once Module 20's Farmer Portal has
 * geographically diverse farmer-submitted listings — is adding that
 * parameter back exactly here, when it's actually needed.
 */
public interface WeatherService {

    Optional<WeatherSnapshot> fetchCurrentWeather();
}
