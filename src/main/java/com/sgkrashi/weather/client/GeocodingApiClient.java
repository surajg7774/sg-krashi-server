package com.sgkrashi.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.weather.dto.response.GeocodingResultResponse;
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
 * Open-Meteo's free geocoding API (geocoding-api.open-meteo.com) — same
 * vendor {@code WeatherApiClient} already calls for forecasts, same no-
 * API-key deal. Forward geocoding only (name/pincode -> coordinates); this
 * project deliberately doesn't do reverse geocoding (coordinates -> name)
 * anywhere — see {@code PublicWeatherResponse}'s Javadoc for why.
 *
 * <p>Mirrors {@code WeatherApiClient}'s WebClient/error-handling shape
 * exactly (inline-built client, blocking call with a timeout, the same
 * three-way catch into one dedicated unchecked exception).
 */
@Component
public class GeocodingApiClient {

    private static final Logger log = LoggerFactory.getLogger(GeocodingApiClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int RESULT_COUNT = 8;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeocodingApiClient(
            @Value("${app.geocoding.base-url:https://geocoding-api.open-meteo.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    /**
     * @throws GeocodingUnavailableException on any network/parse/response failure.
     */
    public List<GeocodingResultResponse> search(String query) {
        String responseBody;
        try {
            responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search")
                            .queryParam("name", query)
                            .queryParam("count", RESULT_COUNT)
                            .queryParam("language", "en")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("Open-Meteo geocoding returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
            throw new GeocodingUnavailableException("Geocoding API returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("Open-Meteo geocoding unreachable: {}", ex.getMessage());
            throw new GeocodingUnavailableException("Geocoding API is unreachable", ex);
        } catch (Exception ex) {
            log.warn("Open-Meteo geocoding call failed", ex);
            throw new GeocodingUnavailableException("Geocoding API call failed", ex);
        }

        return parse(responseBody);
    }

    private List<GeocodingResultResponse> parse(String responseBody) {
        try {
            OpenMeteoGeocodingResponse response = objectMapper.readValue(responseBody, OpenMeteoGeocodingResponse.class);
            // A query with zero matches omits the "results" key entirely
            // (not even an empty array) — Open-Meteo's own documented
            // behavior, not a malformed response.
            if (response.results() == null) {
                return List.of();
            }
            return response.results().stream()
                    .map(r -> new GeocodingResultResponse(r.name(), r.admin1(), r.country(), r.latitude(), r.longitude()))
                    .toList();
        } catch (Exception ex) {
            log.warn("Could not parse Open-Meteo geocoding response: {}", responseBody, ex);
            throw new GeocodingUnavailableException("Geocoding API returned an invalid response", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenMeteoGeocodingResponse(List<Result> results) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Result(
                String name,
                String admin1,
                String country,
                double latitude,
                double longitude,
                @JsonProperty("country_code") String countryCode
        ) {
        }
    }
}
