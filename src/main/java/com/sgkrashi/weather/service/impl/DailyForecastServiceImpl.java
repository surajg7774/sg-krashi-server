package com.sgkrashi.weather.service.impl;

import com.sgkrashi.weather.client.DailyForecastApiClient;
import com.sgkrashi.weather.client.DailyForecastUnavailableException;
import com.sgkrashi.weather.dto.response.DailyForecastPointResponse;
import com.sgkrashi.weather.service.DailyForecastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-location, in-memory TTL cache around {@link DailyForecastApiClient}.
 * The public weather endpoint takes arbitrary coordinates, so (unlike
 * {@code WeatherServiceImpl}'s single-slot cache for the one fixed farm
 * location) this has to be keyed — and bounded, since the key space is
 * user-controlled. Same 45-minute TTL as that cache: a daily forecast moves
 * slower than that.
 *
 * <p>Coordinates are rounded to 2 decimal places (~1 km) for the key —
 * Open-Meteo's model grid is ~10 km, so nearby requests would get the same
 * answer anyway and now share one upstream call. Failures are never cached,
 * and stale entries are never served past the TTL.
 */
@Service
public class DailyForecastServiceImpl implements DailyForecastService {

    private static final Logger log = LoggerFactory.getLogger(DailyForecastServiceImpl.class);
    static final Duration CACHE_TTL = Duration.ofMinutes(45);
    static final int MAX_ENTRIES = 500;

    private final DailyForecastApiClient client;
    private final Clock clock;
    private final Map<String, CachedForecast> cache = new ConcurrentHashMap<>();

    @Autowired
    public DailyForecastServiceImpl(DailyForecastApiClient client) {
        this(client, Clock.systemUTC());
    }

    DailyForecastServiceImpl(DailyForecastApiClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<DailyForecastPointResponse> getDailyForecast(double latitude, double longitude) {
        String key = cacheKey(latitude, longitude);
        Instant now = clock.instant();

        CachedForecast hit = cache.get(key);
        if (hit != null && Duration.between(hit.fetchedAt(), now).compareTo(CACHE_TTL) < 0) {
            return hit.points();
        }

        try {
            List<DailyForecastPointResponse> points = client.fetch(latitude, longitude);
            store(key, new CachedForecast(points, now), now);
            return points;
        } catch (DailyForecastUnavailableException ex) {
            // Deliberately not falling back to the expired entry (same reasoning
            // as WeatherServiceImpl): the chart shows nothing rather than a
            // forecast known to be stale.
            log.warn("Daily forecast unavailable: {}", ex.getClass().getSimpleName());
            cache.remove(key);
            return List.of();
        }
    }

    private void store(String key, CachedForecast entry, Instant now) {
        if (cache.size() >= MAX_ENTRIES && !cache.containsKey(key)) {
            cache.values().removeIf(e -> Duration.between(e.fetchedAt(), now).compareTo(CACHE_TTL) >= 0);
            if (cache.size() >= MAX_ENTRIES) {
                // Still full of live entries — drop the oldest to stay bounded.
                cache.entrySet().stream()
                        .min((a, b) -> a.getValue().fetchedAt().compareTo(b.getValue().fetchedAt()))
                        .ifPresent(oldest -> cache.remove(oldest.getKey()));
            }
        }
        cache.put(key, entry);
    }

    private static String cacheKey(double latitude, double longitude) {
        return "%.2f,%.2f".formatted(latitude, longitude);
    }

    int cacheSize() {
        return cache.size();
    }

    private record CachedForecast(List<DailyForecastPointResponse> points, Instant fetchedAt) {
    }
}
