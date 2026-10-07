package com.sgkrashi.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.auth.controller.AuthController;
import com.sgkrashi.auth.ratelimit.AuthRateLimiters;
import com.sgkrashi.auth.ratelimit.LoginRateLimiter;
import com.sgkrashi.auth.service.AuthService;
import com.sgkrashi.common.exception.GlobalExceptionHandler;
import com.sgkrashi.common.ratelimit.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Proves, against a real embedded Tomcat configured by the real {@code application-prod.yml}, that a
 * client cannot choose the address it is rate-limited under by sending its own {@code X-Forwarded-For}.
 *
 * <p>The test plays the part of Railway's edge: it connects from 127.0.0.1 (one of the configured internal
 * proxies, like the edge's private fdXX:: address) and sends the headers an edge would, including a forged
 * leading X-Forwarded-For entry and the real client address appended on the right.
 */
@SpringBootTest(classes = ClientIpResolutionTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ClientIpResolutionTest {

    @Configuration
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class, EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class})
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class TestApp {
        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(true);
        }

        @Bean
        AuthRateLimiters authRateLimiters(RateLimitProperties properties) {
            return new AuthRateLimiters(properties);
        }

        @Bean
        LoginRateLimiter loginRateLimiter(RateLimitProperties properties) {
            return new LoginRateLimiter(properties);
        }

        @Bean
        AuthController authController(AuthRateLimiters limiters, LoginRateLimiter login, ClientIpResolver resolver) {
            return new AuthController(mock(AuthService.class), login, limiters, resolver);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        ProbeController probeController(ClientIpResolver resolver) {
            return new ProbeController(resolver);
        }
    }

    @RestController
    static class ProbeController {
        private final ClientIpResolver resolver;

        ProbeController(ClientIpResolver resolver) {
            this.resolver = resolver;
        }

        @GetMapping("/probe/whoami")
        public String whoami(HttpServletRequest request) {
            return "{\"key\":\"" + resolver.resolve(request) + "\",\"remoteAddr\":\"" + request.getRemoteAddr()
                    + "\",\"secure\":" + request.isSecure() + ",\"scheme\":\"" + request.getScheme()
                    + "\",\"serverName\":\"" + request.getServerName() + "\"}";
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    private JsonNode whoami(String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/probe/whoami"));
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return json.readTree(http.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    private HttpResponse<String> forgotPassword(String email, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\"}"));
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------------ resolution

    @Test
    void theClientAddressTheEdgeAppendedIsUsed() throws Exception {
        assertEquals("203.0.113.7", whoami("X-Forwarded-For", "203.0.113.7").get("key").asText());
    }

    @Test
    void addressesAClientForgesAtTheFrontOfXForwardedForAreIgnored() throws Exception {
        // The client sent "X-Forwarded-For: 9.9.9.9"; the edge appended the real address after it.
        assertEquals("203.0.113.7", whoami("X-Forwarded-For", "9.9.9.9, 203.0.113.7").get("key").asText());
        assertEquals("203.0.113.7", whoami("X-Forwarded-For", "1.1.1.1, 8.8.8.8, 9.9.9.9, 203.0.113.7").get("key").asText());
        // Forging an internal-looking address does not help either.
        assertEquals("203.0.113.7", whoami("X-Forwarded-For", "10.0.0.1, 192.168.0.9, 203.0.113.7").get("key").asText());
    }

    @Test
    void anInternalHopAfterTheClientIsSkipped() throws Exception {
        assertEquals("203.0.113.7",
                whoami("X-Forwarded-For", "9.9.9.9, 203.0.113.7, fd12:3456:789a::5").get("key").asText());
    }

    @Test
    void withNoForwardingHeaderTheKeyIsTheConnectionItselfNotAClientSuppliedValue() throws Exception {
        JsonNode me = whoami();
        assertEquals("127.0.0.1", me.get("key").asText());
        assertEquals("127.0.0.1", me.get("remoteAddr").asText());
    }

    @Test
    void xRealIpIsOnlyAFallbackAndNeverBeatsAResolvedForwardedAddress() throws Exception {
        assertEquals("203.0.113.7",
                whoami("X-Forwarded-For", "203.0.113.7", "X-Real-IP", "5.6.7.8").get("key").asText());
        // No usable X-Forwarded-For at all: the platform's X-Real-IP is used rather than one shared key.
        assertEquals("5.6.7.8", whoami("X-Real-IP", "5.6.7.8").get("key").asText());
    }

    @Test
    void theHttpsSchemeFromXForwardedProtoIsStillHonoured() throws Exception {
        // Spring Security emits the HSTS header only for requests it sees as secure.
        JsonNode viaEdge = whoami("X-Forwarded-Proto", "https", "X-Forwarded-For", "203.0.113.7");
        assertTrue(viaEdge.get("secure").asBoolean());
        assertEquals("https", viaEdge.get("scheme").asText());
        assertFalse(whoami().get("secure").asBoolean());
    }

    @Test
    void theForwardedHostIsStillHonoured() throws Exception {
        JsonNode viaEdge = whoami("X-Forwarded-Host", "api.example.test", "X-Forwarded-For", "203.0.113.7");
        assertEquals("api.example.test", viaEdge.get("serverName").asText());
    }

    // ------------------------------------------------------------------ end to end through the real controller

    @Test
    void rotatingForgedForwardedForValuesDoesNotBuyAFreshAllowance() throws Exception {
        String realClient = "203.0.113.50";
        // Default: 20 forgot-password requests per client IP per 15 minutes. Each request carries a different
        // forged leading X-Forwarded-For value and a different email (so the per-email limit is not the one hit).
        for (int i = 1; i <= 20; i++) {
            HttpResponse<String> ok = forgotPassword("rotating" + i + "@example.test",
                    "X-Forwarded-For", "7." + i + ".8." + i + ", " + realClient);
            assertEquals(200, ok.statusCode(), "request " + i);
        }
        HttpResponse<String> blocked = forgotPassword("rotating21@example.test",
                "X-Forwarded-For", "99.99.99.99, " + realClient);
        assertEquals(429, blocked.statusCode());
        assertNotNull(blocked.headers().firstValue("Retry-After").orElse(null), "Retry-After header");
        assertTrue(Long.parseLong(blocked.headers().firstValue("Retry-After").get()) > 0);
        JsonNode body = json.readTree(blocked.body());
        assertFalse(body.get("success").asBoolean());
        assertEquals("RATE_LIMIT_EXCEEDED", body.get("error").get("code").asText());

        // A genuinely different client is unaffected.
        assertEquals(200, forgotPassword("someone-else@example.test", "X-Forwarded-For", "198.51.100.1").statusCode());
    }

    @Test
    void oneVictimsInboxIsProtectedEvenWhenTheAttackUsesManyAddresses() throws Exception {
        // Default: 5 forgot-password requests per email per hour, whichever address they come from.
        for (int i = 1; i <= 5; i++) {
            assertEquals(200, forgotPassword("victim@example.test", "X-Forwarded-For", "192.0.2." + i).statusCode(), "request " + i);
        }
        HttpResponse<String> blocked = forgotPassword("Victim@Example.test", "X-Forwarded-For", "192.0.2.200");
        assertEquals(429, blocked.statusCode(), "letter case must not give a different allowance");
        assertNotNull(blocked.headers().firstValue("Retry-After").orElse(null));
    }
}
