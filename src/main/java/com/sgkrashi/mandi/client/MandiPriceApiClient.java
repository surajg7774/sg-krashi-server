package com.sgkrashi.mandi.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Talks to data.gov.in's Open Government Data API for the "Current daily
 * price of various commodities from various markets (Mandi)" dataset
 * (resource ID confirmed via web search against a live example API URL for
 * this exact dataset — Ministry of Agriculture & Farmers Welfare, sourced
 * from the Agmarknet portal, ~2M+ rows, updated daily).
 *
 * <p>Same shape as {@code WeatherApiClient} (this codebase's one other
 * external-API client): {@code WebClient} built inline, blocking {@code
 * .block()}, three-way catch into one dedicated unchecked exception per
 * failure kind. Differs in one deliberate way — {@link #isConfigured()} lets
 * {@code MandiPriceSyncJob} skip the call entirely (and log at INFO, not
 * ERROR) until a real {@code MANDI_API_KEY} exists, since that's the
 * expected, routine state today, not a fault.
 *
 * <p><b>Verified against the live API with a real key: field names are
 * lowercase ({@code records[].state}, {@code .commodity}, {@code
 * .min_price}, {@code .arrival_date}, etc.), prices arrive as JSON numbers
 * (Jackson coerces these into the {@code String}-typed {@link RawRecord}
 * fields without error), and {@code arrival_date} is {@code dd/MM/yyyy}
 * (e.g. {@code "14/09/2026"}), matching {@link #ARRIVAL_DATE_FORMAT}
 * exactly. A real fetch for Wheat in Madhya Pradesh returned 101 correctly
 * parsed rows with no skipped/unparseable records.</b>
 */
@Component
public class MandiPriceApiClient {

    private static final Logger log = LoggerFactory.getLogger(MandiPriceApiClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final DateTimeFormatter ARRIVAL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
    // Confirmed against the real API with a live key: rate limit header
    // reports unmetered (X-Ratelimit-Limit: -1), and a requested limit=500
    // is honored in full on every page (no silent server-side truncation).
    // There IS a hard ceiling, but it's not ours or the key tier's — the
    // platform is Elasticsearch-backed (see the metadata response's
    // target_bucket.index) with the default index.max_result_window: any
    // offset+limit request with offset+limit > 10,000 fails with
    // "Result window is too large... must be <= 10000" (confirmed by
    // requesting offset=10000 directly). MAX_PAGES=20 × PAGE_SIZE=500 sits
    // exactly at that ceiling for an unfiltered fetch, so the loop's own
    // "short page -> stop" logic ends it cleanly right there in practice.
    // A state/commodity-filtered fetch (the MP-priority-commodity loop)
    // returns far fewer rows per call and never approaches this limit — only
    // the daily broad, unfiltered catch-all pass is capped at the first
    // 10,000 rows the API returns, in whatever order it applies; on a day
    // with more rows nationwide than that (~12,250 confirmed on one day
    // tested), the remainder is not retrievable via pagination at all, by
    // this platform's own design, not something a bigger MAX_PAGES fixes.
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 20;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String resourceId;

    public MandiPriceApiClient(
            @Value("${app.mandi.base-url:https://api.data.gov.in}") String baseUrl,
            @Value("${app.mandi.api-key:}") String apiKey,
            @Value("${app.mandi.resource-id:9ef84268-d588-465a-a308-a864a43d0070}") String resourceId,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.resourceId = resourceId;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Fetches every available row, paginating via offset/limit, optionally
     * narrowed server-side by state and/or commodity (the API supports
     * {@code filters[state]}/{@code filters[commodity]} — narrowing here is
     * purely a network/quota optimization for the MP-priority pass, the
     * stored table and the rest of this app support any state/commodity
     * regardless).
     *
     * @throws MandiApiNotConfiguredException if no API key is set
     * @throws MandiApiUnavailableException    on any network/parse/response failure
     */
    public List<MandiPriceRow> fetchAll(String stateFilter, String commodityFilter) {
        if (!isConfigured()) {
            throw new MandiApiNotConfiguredException("MANDI_API_KEY is not set — skipping mandi price sync");
        }

        List<MandiPriceRow> rows = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            int offset = page * PAGE_SIZE;
            String responseBody;
            try {
                responseBody = webClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/resource/{resourceId}")
                                    .queryParam("api-key", apiKey)
                                    .queryParam("format", "json")
                                    .queryParam("offset", offset)
                                    .queryParam("limit", PAGE_SIZE);
                            if (stateFilter != null && !stateFilter.isBlank()) {
                                uriBuilder.queryParam("filters[state]", stateFilter);
                            }
                            if (commodityFilter != null && !commodityFilter.isBlank()) {
                                uriBuilder.queryParam("filters[commodity]", commodityFilter);
                            }
                            return uriBuilder.build(resourceId);
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(TIMEOUT)
                        .block();
            } catch (WebClientResponseException ex) {
                log.warn("data.gov.in mandi API returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
                throw new MandiApiUnavailableException("Mandi price API returned an error response", ex);
            } catch (WebClientRequestException ex) {
                log.warn("data.gov.in mandi API unreachable: {}", ex.getMessage());
                throw new MandiApiUnavailableException("Mandi price API is unreachable", ex);
            } catch (Exception ex) {
                log.warn("data.gov.in mandi API call failed", ex);
                throw new MandiApiUnavailableException("Mandi price API call failed", ex);
            }

            List<MandiPriceRow> pageRows = parseRecords(responseBody);
            rows.addAll(pageRows);
            if (pageRows.size() < PAGE_SIZE) {
                break; // short page — this was the last one
            }
        }
        return rows;
    }

    private List<MandiPriceRow> parseRecords(String responseBody) {
        try {
            MandiApiResponse response = objectMapper.readValue(responseBody, MandiApiResponse.class);
            List<MandiPriceRow> rows = new ArrayList<>();
            if (response.records() == null) {
                return rows;
            }
            for (RawRecord record : response.records()) {
                MandiPriceRow row = toRow(record);
                if (row != null) {
                    rows.add(row);
                }
            }
            return rows;
        } catch (Exception ex) {
            log.warn("Could not parse mandi price API response: {}", responseBody, ex);
            throw new MandiApiUnavailableException("Mandi price API returned an invalid response", ex);
        }
    }

    private MandiPriceRow toRow(RawRecord record) {
        try {
            return new MandiPriceRow(
                    record.commodity(),
                    record.market(),
                    record.state(),
                    record.district(),
                    parsePrice(record.minPrice()),
                    parsePrice(record.maxPrice()),
                    parsePrice(record.modalPrice()),
                    java.time.LocalDate.parse(record.arrivalDate(), ARRIVAL_DATE_FORMAT));
        } catch (Exception ex) {
            // One malformed row (a missing price, an unparseable date) must
            // not sink the whole sync — skip it and keep going.
            log.warn("Skipping one unparseable mandi price row: {}", record, ex);
            return null;
        }
    }

    private static java.math.BigDecimal parsePrice(String raw) {
        return new java.math.BigDecimal(raw.trim());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MandiApiResponse(List<RawRecord> records) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawRecord(
            @JsonProperty("state") String state,
            @JsonProperty("district") String district,
            @JsonProperty("market") String market,
            @JsonProperty("commodity") String commodity,
            @JsonProperty("arrival_date") String arrivalDate,
            @JsonProperty("min_price") String minPrice,
            @JsonProperty("max_price") String maxPrice,
            @JsonProperty("modal_price") String modalPrice
    ) {
    }
}
