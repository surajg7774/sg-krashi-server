package com.sgkrashi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.auth.security.JwtAuthenticationFilter;
import com.sgkrashi.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * Platform-wide Spring Security configuration: stateless JWT authentication,
 * a public allow-list for auth/health/docs endpoints, and everything else
 * requiring authentication by default. Method-level {@code @PreAuthorize}
 * checks (used from Module 2 onward for role-gated endpoints) are enabled here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/api/v1/health",
            "/api/v1/inquiries",
            "/api/v1/payments/webhook",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    // GET-only, scoped by HTTP method rather than lumped into PUBLIC_ENDPOINTS —
    // these paths will gain authenticated write endpoints (Module 15's Admin
    // product management), and a blanket path-based permitAll() here would
    // silently make those public too the moment they're added.
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/products/**",
            "/api/v1/product-categories/**",
            "/api/v1/crop-listings/**",
            "/api/v1/crop-categories/**",
            "/api/v1/equipment/**",
            "/api/v1/farm-stay/**",
            "/api/v1/bookings/availability",
            "/api/v1/bookings/availability/**",
            "/api/v1/reviews",
            "/uploads/**",
            "/api/v1/cms/content-blocks",
            "/api/v1/search",
            // A Guest needs this to populate the crop dropdown before they can
            // even attempt an analysis (Guest Access refinement) — the analyze
            // endpoint itself being public isn't enough on its own.
            "/api/v1/ai/crop-doctor/supported-crops"
    };

    // POST-only, scoped by method for the same reason as PUBLIC_GET_ENDPOINTS —
    // only the analyze action itself is public; /scans, /scans/{id}, DELETE,
    // and the PDF report stay behind the default anyRequest().authenticated()
    // rule below, since those are the "save/history/download" actions that
    // still require login (Guest Access refinement).
    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/v1/ai/crop-doctor/analyze"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper,
            CorsConfigurationSource corsConfigurationSource
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Authentication is required to access this resource"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                                        "ACCESS_DENIED", "You do not have permission to perform this action")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = ApiErrorResponse.of(code, message, List.of());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
