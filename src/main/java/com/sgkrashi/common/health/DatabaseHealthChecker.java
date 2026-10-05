package com.sgkrashi.common.health;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Answers one question for the public {@code GET /health}: does the database
 * respond to a trivial query right now? Built for an anonymous endpoint, so
 * it is defensive in two ways:
 *
 * <ul>
 *   <li><b>Cheap under hammering</b> — the result is cached for a few
 *       seconds, so any number of callers cost at most one {@code SELECT 1}
 *       per window (a 5-minute uptime poll just always misses the cache).</li>
 *   <li><b>Never hangs the caller</b> — the probe runs on its own thread
 *       with a hard timeout. A dead database would otherwise block on
 *       Hikari's 30-second connection wait, which an uptime monitor reads
 *       as a timeout rather than a clean "down".</li>
 * </ul>
 *
 * Failures are logged by exception type only (no stack, no message): this
 * class's output feeds an unauthenticated endpoint, so nothing about the
 * connection should leak through it, and the log line is enough to alert on.
 */
@Component
public class DatabaseHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthChecker.class);
    static final Duration CACHE_TTL = Duration.ofSeconds(5);
    static final Duration CHECK_TIMEOUT = Duration.ofSeconds(3);
    private static final int QUERY_TIMEOUT_SECONDS = 2;

    private final BooleanSupplier probe;
    private final Clock clock;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "health-db-check");
        thread.setDaemon(true);
        return thread;
    });
    private Result last;

    @Autowired
    public DatabaseHealthChecker(DataSource dataSource) {
        this(jdbcProbe(dataSource), Clock.systemUTC(), CHECK_TIMEOUT);
    }

    DatabaseHealthChecker(BooleanSupplier probe, Clock clock, Duration timeout) {
        this.probe = probe;
        this.clock = clock;
        this.timeout = timeout;
    }

    /** Never throws: any failure, timeout or unexpected answer is {@code false}. */
    public synchronized boolean isDatabaseUp() {
        Instant now = clock.instant();
        if (last != null && Duration.between(last.checkedAt(), now).compareTo(CACHE_TTL) < 0) {
            return last.up();
        }
        boolean up = runProbe();
        last = new Result(up, clock.instant());
        return up;
    }

    private boolean runProbe() {
        Future<Boolean> future = executor.submit(probe::getAsBoolean);
        try {
            return Boolean.TRUE.equals(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("Database health check timed out after {} ms", timeout.toMillis());
            return false;
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | RuntimeException ex) {
            Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
            log.warn("Database health check failed: {}", cause.getClass().getSimpleName());
            return false;
        }
    }

    // A dedicated JdbcTemplate (not the shared bean) so its query timeout
    // doesn't leak onto the application's own queries.
    private static BooleanSupplier jdbcProbe(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return () -> Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record Result(boolean up, Instant checkedAt) {
    }
}
