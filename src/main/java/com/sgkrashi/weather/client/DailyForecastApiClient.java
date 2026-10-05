package com.sgkrashi.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.weather.dto.response.DailyForecastPointResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Open-Meteo's 7-day daily forecast (max/min temperature + rain) for the
 * public weather sparkline. A deliberately separate call from {@code
 * com.sgkrashi.ai.weather.client.WeatherApiClient}: that client's request
 * shape (past_days=3, forecast_days=2, position-based indexing) feeds the AI
 * grounding context and the advisory job, so it stays untouched — this one
 * just asks Open-Meteo for what the chart needs. Same base-url property, so
 * nothing new to configure.
 *
 * <p>Mirrors {@code WeatherApiClient}'s WebClient/error-handling shape.
 */
@Component
public class DailyForecastApiClient {

    private static final Logger log = LoggerFactory.getLogger(DailyForecastApiClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int FORECAST_DAYS = 7;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DailyForecastApiClient(
            @Value("${app.weather.base-url:https://api.open-meteo.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    /**
     * @throws DailyForecastUnavailableException on any network/parse/response failure, or if the response held no usable day.
     */
    public List<DailyForecastPointResponse> fetch(double latitude, double longitude) {
        String responseBody;
        try {
            responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum")
                            .queryParam("forecast_days", FORECAST_DAYS)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("Open-Meteo daily forecast returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
            throw new DailyForecastUnavailableException("Daily forecast API returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("Open-Meteo daily forecast unreachable: {}", ex.getMessage());
            throw new DailyForecastUnavailableException("Daily forecast API is unreachable", ex);
        } catch (Exception ex) {
            log.warn("Open-Meteo daily forecast call failed", ex);
            throw new DailyForecastUnavailableException("Daily forecast API call failed", ex);
        }

        return parse(responseBody, objectMapper);
    }

    /**
     * Package-private + static so the parsing (the part with real edge cases:
     * Open-Meteo returns {@code null} for a day it has no value for) is
     * unit-testable without a network call. A day with any missing value is
     * dropped rather than zero-filled — a 0 would plot as a real reading.
     */
    static List<DailyForecastPointResponse> parse(String responseBody, ObjectMapper objectMapper) {
        try {
            OpenMeteoResponse response = objectMapper.readValue(responseBody, OpenMeteoResponse.class);
            Daily daily = response.daily();
            if (daily == null || daily.time() == null || daily.tempMax() == null || daily.tempMin() == null || daily.rain() == null) {
                throw new DailyForecastUnavailableException("Daily forecast response is missing the daily block");
            }
            int days = daily.time().size();
            if (daily.tempMax().size() != days || daily.tempMin().size() != days || daily.rain().size() != days) {
                throw new DailyForecastUnavailableException("Daily forecast response has mismatched series lengths");
            }

            List<DailyForecastPointResponse> points = new ArrayList<>(days);
            for (int i = 0; i < days; i++) {
                Double max = daily.tempMax().get(i);
                Double min = daily.tempMin().get(i);
                Double rain = daily.rain().get(i);
                if (daily.time().get(i) == null || max == null || min == null || rain == null) {
                    continue;
                }
                points.add(new DailyForecastPointResponse(LocalDate.parse(daily.time().get(i)), max, min, rain));
            }
            if (points.isEmpty()) {
                throw new DailyForecastUnavailableException("Daily forecast response had no usable day");
            }
            return List.copyOf(points);
        } catch (DailyForecastUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not parse Open-Meteo daily forecast response", ex);
            throw new DailyForecastUnavailableException("Daily forecast API returned an invalid response", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenMeteoResponse(Daily daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Daily(
            List<String> time,
            @JsonProperty("temperature_2m_max") List<Double> tempMax,
            @JsonProperty("temperature_2m_min") List<Double> tempMin,
            @JsonProperty("precipitation_sum") List<Double> rain
    ) {
    }
}
