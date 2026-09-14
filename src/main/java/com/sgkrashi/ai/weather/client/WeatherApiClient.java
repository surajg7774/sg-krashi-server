package com.sgkrashi.ai.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.ai.weather.dto.WeatherSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * Talks to Open-Meteo (api.open-meteo.com) — the REST fallback the RAG task
 * spec explicitly sanctions ("if no suitable weather MCP server can be
 * found, fall back to a direct REST call... e.g. Open-Meteo"). See {@code
 * WeatherService}'s own Javadoc for why this project uses that fallback
 * rather than a real weather MCP connection: every weather MCP server found
 * during research was either a local stdio subprocess (a dev-tool pattern —
 * fine for spawning inside an editor session, not something a deployed
 * Spring Boot service on Railway should exec as a child process just to
 * read a temperature) or a hosted endpoint run by an individual GitHub
 * project on free hosting with no SLA, several of which turned out to be
 * thin wrappers around this exact same Open-Meteo API anyway. No API key
 * required — nothing new to configure in Railway for this feature.
 *
 * <p>Isolated behind this one class (mirrors {@code
 * com.sgkrashi.ai.embedding.EmbeddingServiceImpl}'s WebClient pattern) so
 * {@code WeatherServiceImpl} — and everything upstream of it — has no idea
 * which weather data source is actually in use.
 */
@Component
public class WeatherApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherApiClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    // past_days covers the recent-rainfall figure; forecast_days=2 (today +
    // tomorrow) is only so the forecast summary can say something about
    // tomorrow specifically, not today's already-current conditions restated.
    private static final int PAST_DAYS = 3;
    private static final int FORECAST_DAYS = 2;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WeatherApiClient(
            @Value("${app.weather.base-url:https://api.open-meteo.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    /**
     * @throws WeatherUnavailableException on any network/parse/response failure — callers (only {@code WeatherServiceImpl}) always catch this and degrade gracefully.
     */
    public WeatherSnapshot fetch(double latitude, double longitude) {
        String responseBody;
        try {
            responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation")
                            // temperature_2m_min added for the weather advisory job's frost-risk
                            // rule (Facility Feature #3) — precipitation_sum already covered the
                            // rain-forecast/spraying rule via forecastSummary's tomorrow-index logic.
                            .queryParam("daily", "precipitation_sum,temperature_2m_min")
                            .queryParam("past_days", PAST_DAYS)
                            .queryParam("forecast_days", FORECAST_DAYS)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("Open-Meteo returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
            throw new WeatherUnavailableException("Weather API returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("Open-Meteo unreachable: {}", ex.getMessage());
            throw new WeatherUnavailableException("Weather API is unreachable", ex);
        } catch (Exception ex) {
            log.warn("Open-Meteo call failed", ex);
            throw new WeatherUnavailableException("Weather API call failed", ex);
        }

        return parse(responseBody);
    }

    private WeatherSnapshot parse(String responseBody) {
        try {
            OpenMeteoResponse response = objectMapper.readValue(responseBody, OpenMeteoResponse.class);
            List<Double> dailyPrecipitation = response.daily().precipitationSum();

            // Position-based, not date-based: past_days=3 puts the 3 past
            // days first in the array (indices 0-2), then today, then any
            // forecast days — true regardless of what today's actual date
            // is, so nothing here needs to parse response.daily().time().
            double recentRainfall = dailyPrecipitation.stream()
                    .limit(PAST_DAYS)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            int tomorrowIndex = PAST_DAYS + 1;
            double tomorrowPrecipitation = tomorrowIndex < dailyPrecipitation.size() ? dailyPrecipitation.get(tomorrowIndex) : 0.0;
            String forecastSummary = tomorrowPrecipitation > 1.0
                    ? "Rain expected tomorrow (~%.1fmm)".formatted(tomorrowPrecipitation)
                    : "No significant rain expected tomorrow";

            List<Double> dailyMinTemp = response.daily().temperature2mMin();
            double tomorrowMinTemp = dailyMinTemp != null && tomorrowIndex < dailyMinTemp.size()
                    ? dailyMinTemp.get(tomorrowIndex)
                    : response.current().temperature2m();

            return new WeatherSnapshot(
                    response.current().temperature2m(),
                    response.current().relativeHumidity2m(),
                    recentRainfall,
                    forecastSummary,
                    tomorrowMinTemp,
                    tomorrowPrecipitation);
        } catch (Exception ex) {
            log.warn("Could not parse Open-Meteo response: {}", responseBody, ex);
            throw new WeatherUnavailableException("Weather API returned an invalid response", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenMeteoResponse(Current current, Daily daily) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Current(
                @JsonProperty("temperature_2m") double temperature2m,
                @JsonProperty("relative_humidity_2m") double relativeHumidity2m
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Daily(
                @JsonProperty("precipitation_sum") List<Double> precipitationSum,
                @JsonProperty("temperature_2m_min") List<Double> temperature2mMin
        ) {
        }
    }
}
