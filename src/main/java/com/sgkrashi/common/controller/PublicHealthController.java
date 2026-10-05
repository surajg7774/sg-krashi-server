package com.sgkrashi.common.controller;

import com.sgkrashi.common.health.DatabaseHealthChecker;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public, unauthenticated readiness endpoint for uptime monitors:
 * {@code GET /health} -> 200 {@code {"status":"UP"}} when the app is up and
 * the database answers a trivial query, 503 {@code {"status":"DOWN"}}
 * otherwise. Deliberately nothing else in the body — no component details,
 * versions, URLs or error text.
 *
 * <p>Sits beside (not in place of) {@link HealthController}'s
 * {@code /api/v1/health}, which is a pure liveness ping inside the standard
 * API envelope and never touches the database; that one is unchanged so
 * anything already calling it keeps working. Spring maps HEAD onto this GET
 * handler automatically, which matters because some uptime monitors probe
 * with HEAD.
 */
@RestController
public class PublicHealthController {

    private final DatabaseHealthChecker databaseHealthChecker;

    public PublicHealthController(DatabaseHealthChecker databaseHealthChecker) {
        this.databaseHealthChecker = databaseHealthChecker;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (databaseHealthChecker.isDatabaseUp()) {
            return ResponseEntity.ok(Map.of("status", "UP"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }
}
