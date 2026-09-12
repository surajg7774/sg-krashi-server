package com.sgkrashi.ai.weather.service.impl;

import com.sgkrashi.ai.weather.client.WeatherApiClient;
import com.sgkrashi.ai.weather.client.WeatherUnavailableException;
import com.sgkrashi.ai.weather.dto.WeatherSnapshot;
import com.sgkrashi.ai.weather.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The one fixed V1 location — the platform's own farm (see {@code
 * platform_knowledge_entries}' "Contact information" entry for the same
 * address elsewhere in the app). Approximated to Khandwa town's coordinates
 * rather than Sirpur village's exact position: precise village-level
 * coordinates aren't readily available, and at the resolution a weather
 * forecast model actually operates on (multi-kilometer grid cells), a
 * village versus its district town is not a meaningfully different
 * forecast point.
 */
@Service
public class WeatherServiceImpl implements WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherServiceImpl.class);
    private static final double FARM_LATITUDE = 21.83;
    private static final double FARM_LONGITUDE = 76.35;
    // Weather doesn't change meaningfully faster than this for the purpose
    // it's used for here (grounding context, not a live dashboard) — caching
    // avoids an external call on every single scan/chat message.
    private static final Duration CACHE_TTL = Duration.ofMinutes(45);

    private final WeatherApiClient weatherApiClient;
    private volatile CachedSnapshot cache;

    public WeatherServiceImpl(WeatherApiClient weatherApiClient) {
        this.weatherApiClient = weatherApiClient;
    }

    @Override
    public synchronized Optional<WeatherSnapshot> fetchCurrentWeather() {
        CachedSnapshot current = cache;
        if (current != null && Duration.between(current.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return Optional.of(current.snapshot());
        }

        try {
            WeatherSnapshot snapshot = weatherApiClient.fetch(FARM_LATITUDE, FARM_LONGITUDE);
            cache = new CachedSnapshot(snapshot, Instant.now());
            return Optional.of(snapshot);
        } catch (WeatherUnavailableException ex) {
            // Never propagated — a weather outage means "no grounding this
            // time," not a failed scan or chat reply. If a stale cache entry
            // exists, deliberately don't fall back to it here: it already
            // failed the freshness check above, and serving weather that's
            // known to be more than CACHE_TTL old as if it were current would
            // be its own kind of dishonesty toward the model/user.
            log.warn("Weather unavailable, proceeding without weather grounding: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private record CachedSnapshot(WeatherSnapshot snapshot, Instant fetchedAt) {
    }
}
