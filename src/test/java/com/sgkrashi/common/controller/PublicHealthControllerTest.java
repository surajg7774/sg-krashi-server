package com.sgkrashi.common.controller;

import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.JwtAuthenticationFilter;
import com.sgkrashi.auth.security.JwtTokenProvider;
import com.sgkrashi.common.health.DatabaseHealthChecker;
import com.sgkrashi.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real {@link SecurityConfig} (so "no auth needed" is a statement
 * about the actual allow-list, not about security being switched off) with the
 * database check mocked for the UP and DOWN paths.
 */
@WebMvcTest({PublicHealthController.class, HealthController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, PublicHealthControllerTest.CorsStub.class})
class PublicHealthControllerTest {

    @TestConfiguration
    static class CorsStub {
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthChecker databaseHealthChecker;

    // JwtAuthenticationFilter's dependencies — untouched on a request with no Authorization header.
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void upIs200WithExactlyStatusUpAndNoAuth() throws Exception {
        when(databaseHealthChecker.isDatabaseUp()).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    void downIs503WithExactlyStatusDownAndNoAuth() throws Exception {
        when(databaseHealthChecker.isDatabaseUp()).thenReturn(false);

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    }

    @Test
    void headIsAnsweredToo_sinceSomeMonitorsProbeWithHead() throws Exception {
        when(databaseHealthChecker.isDatabaseUp()).thenReturn(true);

        mockMvc.perform(head("/health")).andExpect(status().isOk());
    }

    @Test
    void onlyGetAndHeadArePublic() throws Exception {
        mockMvc.perform(post("/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void theExistingLivenessEndpointIsUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.message").value("Service is healthy"));
    }

    @Test
    void securityIsGenuinelyEngagedInThisSlice() throws Exception {
        // Guards the tests above against passing vacuously: a secured path is still 401 anonymously.
        String body = mockMvc.perform(get("/api/v1/orders/not-public")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertEquals(true, body.contains("UNAUTHENTICATED"));
    }
}
