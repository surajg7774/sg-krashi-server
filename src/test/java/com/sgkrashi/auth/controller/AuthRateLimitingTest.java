package com.sgkrashi.auth.controller;

import com.sgkrashi.auth.dto.response.AuthResponse;
import com.sgkrashi.auth.ratelimit.AuthRateLimiters;
import com.sgkrashi.auth.ratelimit.LoginRateLimiter;
import com.sgkrashi.auth.service.AuthResult;
import com.sgkrashi.auth.service.AuthService;
import com.sgkrashi.common.exception.GlobalExceptionHandler;
import com.sgkrashi.common.ratelimit.RateLimitProperties;
import com.sgkrashi.common.web.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Through the real web and mobile auth controllers and the real limiters (AuthService mocked): normal use
 * still works under the default limits, and every limited endpoint answers 429 in the standard error shape
 * with a Retry-After header once its limit is passed.
 */
class AuthRateLimitingTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final RequestPostProcessor FROM_CLIENT_A = from("203.0.113.7");

    private AuthService authService;

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private MockMvc mvcWith(Map<String, String> limitProperties) {
        RateLimitProperties props = new Binder(new MapConfigurationPropertySource(limitProperties))
                .bind("app.rate-limit", RateLimitProperties.class)
                .orElseGet(() -> new RateLimitProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        LoginRateLimiter login = new LoginRateLimiter(props);
        AuthRateLimiters limiters = new AuthRateLimiters(props);
        ClientIpResolver resolver = new ClientIpResolver(true);
        return MockMvcBuilders
                .standaloneSetup(
                        new AuthController(authService, login, limiters, resolver),
                        new MobileAuthController(authService, login, limiters, resolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @BeforeEach
    void stubService() {
        authService = mock(AuthService.class);
        AuthResult result = new AuthResult(
                new AuthResponse("access-token", new AuthResponse.UserSummary(1L, "Test User", "t@example.test", List.of("CUSTOMER"))),
                "raw-refresh-token");
        when(authService.login(any())).thenReturn(result);
        when(authService.verifyOtp(any())).thenReturn(result);
        when(authService.loginWithGoogle(any())).thenReturn(result);
        when(authService.refresh(any())).thenReturn(result);
    }

    // every limited call, as (path, JSON body)
    private record Call(String name, String path, String body) {
    }

    private static final List<Call> CALLS = List.of(
            new Call("register", "/api/v1/auth/register", "{\"name\":\"A\",\"email\":\"a@example.test\",\"password\":\"password123\"}"),
            new Call("verify-otp", "/api/v1/auth/verify-otp", "{\"email\":\"a@example.test\",\"otp\":\"123456\"}"),
            new Call("resend-otp", "/api/v1/auth/resend-otp", "{\"email\":\"a@example.test\"}"),
            new Call("google", "/api/v1/auth/google", "{\"idToken\":\"x\"}"),
            new Call("login", "/api/v1/auth/login", "{\"email\":\"a@example.test\",\"password\":\"password123\"}"),
            new Call("forgot-password", "/api/v1/auth/forgot-password", "{\"email\":\"a@example.test\"}"),
            new Call("reset-password", "/api/v1/auth/reset-password", "{\"token\":\"t\",\"newPassword\":\"password123\"}"),
            new Call("mobile login", "/api/v1/auth/mobile/login", "{\"email\":\"a@example.test\",\"password\":\"password123\"}"),
            new Call("mobile google", "/api/v1/auth/mobile/google", "{\"idToken\":\"x\"}"),
            new Call("mobile verify-otp", "/api/v1/auth/mobile/verify-otp", "{\"email\":\"a@example.test\",\"otp\":\"123456\"}"),
            new Call("mobile refresh", "/api/v1/auth/mobile/refresh", "{\"refreshToken\":\"r\"}"));

    private ResultActions perform(MockMvc mvc, Call call, RequestPostProcessor from) throws Exception {
        return mvc.perform(post(call.path()).contentType(JSON).content(call.body()).with(from));
    }

    // ------------------------------------------------------------------ legitimate use keeps working

    @Test
    void aNormalSessionOfAccountActionsSucceedsUnderTheDefaultLimits() throws Exception {
        MockMvc mvc = mvcWith(Map.of());
        for (Call call : CALLS) {
            // Each is used a couple of times, like a real person retrying a step (web and mobile login share one limiter).
            for (int i = 0; i < 2; i++) {
                perform(mvc, call, FROM_CLIENT_A).andExpect(status().is2xxSuccessful());
            }
        }
    }

    @Test
    void manyDifferentPeopleSharingOneCarrierAddressAreNotLockedOutByEachOther() throws Exception {
        MockMvc mvc = mvcWith(Map.of());
        // 15 people behind one address each: forgot their password, reset it, then logged in once.
        for (int person = 0; person < 15; person++) {
            String email = "person" + person + "@example.test";
            mvc.perform(post("/api/v1/auth/forgot-password").contentType(JSON)
                            .content("{\"email\":\"" + email + "\"}").with(FROM_CLIENT_A))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                            .content("{\"token\":\"t" + person + "\",\"newPassword\":\"password123\"}").with(FROM_CLIENT_A))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"email\":\"person0@example.test\",\"password\":\"password123\"}").with(FROM_CLIENT_A))
                .andExpect(status().isOk());
    }

    @Test
    void loginKeepsItsOriginalFiveAttemptsPerFifteenMinutes() throws Exception {
        MockMvc mvc = mvcWith(Map.of());
        Call login = CALLS.stream().filter(c -> c.name().equals("login")).findFirst().orElseThrow();
        for (int i = 0; i < 5; i++) {
            perform(mvc, login, FROM_CLIENT_A).andExpect(status().isOk());
        }
        perform(mvc, login, FROM_CLIENT_A).andExpect(status().isTooManyRequests());
        // Registration is a separate limiter: it is still open for the same address.
        perform(mvc, CALLS.get(0), FROM_CLIENT_A).andExpect(status().is2xxSuccessful());
    }

    // ------------------------------------------------------------------ the 429 answer

    @Test
    void everyLimitedEndpointAnswers429WithTheStandardErrorShapeAndARetryAfterHeader() throws Exception {
        // Everything limited to 2 per hour so the test stays small; the numbers themselves are tested elsewhere.
        MockMvc mvc = mvcWith(Map.ofEntries(
                Map.entry("app.rate-limit.login.max", "2"), Map.entry("app.rate-limit.login.window", "1h"),
                Map.entry("app.rate-limit.register.max", "2"), Map.entry("app.rate-limit.register.window", "1h"),
                Map.entry("app.rate-limit.forgot-password-ip.max", "2"), Map.entry("app.rate-limit.forgot-password-ip.window", "1h"),
                Map.entry("app.rate-limit.reset-password.max", "2"), Map.entry("app.rate-limit.reset-password.window", "1h"),
                Map.entry("app.rate-limit.resend-otp-ip.max", "2"), Map.entry("app.rate-limit.resend-otp-ip.window", "1h"),
                Map.entry("app.rate-limit.verify-otp.max", "2"), Map.entry("app.rate-limit.verify-otp.window", "1h"),
                Map.entry("app.rate-limit.google.max", "2"), Map.entry("app.rate-limit.google.window", "1h"),
                Map.entry("app.rate-limit.refresh.max", "2"), Map.entry("app.rate-limit.refresh.window", "1h")));

        // register, reset-password, forgot-password, resend-otp and mobile refresh each have a limiter of their own;
        // login, verify-otp and google are shared between the web and mobile endpoints and are exercised after.
        String[] onlyOwnLimiter = {"register", "reset-password", "forgot-password", "resend-otp", "mobile refresh"};
        int n = 0;
        for (String name : onlyOwnLimiter) {
            Call call = CALLS.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
            RequestPostProcessor addr = from("198.51.100." + (++n));
            perform(mvc, call, addr).andExpect(status().is2xxSuccessful());
            perform(mvc, call, addr).andExpect(status().is2xxSuccessful());
            perform(mvc, call, addr)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
                    .andExpect(jsonPath("$.error.message").exists());
        }

        // login: web and mobile share one limiter per address.
        RequestPostProcessor loginAddr = from("198.51.100.50");
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("login")).findFirst().orElseThrow(), loginAddr).andExpect(status().isOk());
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("mobile login")).findFirst().orElseThrow(), loginAddr).andExpect(status().isOk());
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("login")).findFirst().orElseThrow(), loginAddr)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        // verify-otp and google: web and mobile share one limiter each.
        RequestPostProcessor otpAddr = from("198.51.100.51");
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("verify-otp")).findFirst().orElseThrow(), otpAddr).andExpect(status().is2xxSuccessful());
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("mobile verify-otp")).findFirst().orElseThrow(), otpAddr).andExpect(status().is2xxSuccessful());
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("mobile verify-otp")).findFirst().orElseThrow(), otpAddr)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        RequestPostProcessor googleAddr = from("198.51.100.52");
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("google")).findFirst().orElseThrow(), googleAddr).andExpect(status().isOk());
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("mobile google")).findFirst().orElseThrow(), googleAddr).andExpect(status().isOk());
        perform(mvc, CALLS.stream().filter(c -> c.name().equals("google")).findFirst().orElseThrow(), googleAddr)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void aBlockedRequestNeverReachesTheServiceSoNoEmailIsSent() throws Exception {
        MockMvc mvc = mvcWith(Map.of("app.rate-limit.forgot-password-email.max", "1", "app.rate-limit.forgot-password-email.window", "1h"));
        Call forgot = CALLS.stream().filter(c -> c.name().equals("forgot-password")).findFirst().orElseThrow();
        perform(mvc, forgot, from("203.0.113.1")).andExpect(status().isOk());
        perform(mvc, forgot, from("203.0.113.2")).andExpect(status().isTooManyRequests());
        perform(mvc, forgot, from("203.0.113.3")).andExpect(status().isTooManyRequests());

        verify(authService, org.mockito.Mockito.times(1)).forgotPassword(any());
        verify(authService, never()).resendOtp(any());
    }

    @Test
    void theWebRefreshEndpointStaysUnlimitedBecauseEveryPageLoadCallsIt() throws Exception {
        MockMvc mvc = mvcWith(Map.of("app.rate-limit.refresh.max", "1", "app.rate-limit.refresh.window", "1h"));
        for (int i = 0; i < 30; i++) {
            mvc.perform(post("/api/v1/auth/refresh").with(FROM_CLIENT_A)).andExpect(status().isOk());
        }
    }
}
