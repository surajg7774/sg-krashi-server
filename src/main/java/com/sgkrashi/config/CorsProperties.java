package com.sgkrashi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds {@code app.cors.allowed-origins} (comma-separated in application.yml)
 * to a strongly typed list. No wildcard origins are permitted.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
