package com.sgkrashi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Restricts cross-origin access to an explicit, configured allow-list of origins.
 * Origins are sourced from {@link CorsProperties} (app.cors.allowed-origins) —
 * never a wildcard "*".
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean rather than a
 * {@code WebMvcConfigurer}, so {@code SecurityConfig} can wire it into Spring
 * Security's own filter chain via {@code .cors(...)}. This matters because a
 * cross-origin preflight (OPTIONS) request carries no Authorization header —
 * if CORS is only handled at the MVC level, Security's
 * {@code anyRequest().authenticated()} rejects the preflight as unauthenticated
 * before it ever reaches MVC's CORS handling, and the browser reports that as a
 * CORS failure rather than a 401.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
