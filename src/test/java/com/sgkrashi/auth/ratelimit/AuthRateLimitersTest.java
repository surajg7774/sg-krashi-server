package com.sgkrashi.auth.ratelimit;

import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.common.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimitersTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    private static RateLimitProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("app.rate-limit", RateLimitProperties.class)
                .orElseGet(() -> new RateLimitProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    private AuthRateLimiters limiters(Map<String, String> values) {
        return new AuthRateLimiters(bind(values), clock);
    }

    private static void callTimes(int times, Runnable call) {
        for (int i = 0; i < times; i++) {
            call.run();
        }
    }

    // ------------------------------------------------------------------ defaults

    @Test
    void theDefaultsAreTheDocumentedNumbers() {
        RateLimitProperties defaults = bind(Map.of());
        assertEquals(5, defaults.login().max());
        assertEquals(Duration.ofMinutes(15), defaults.login().window());
        assertEquals(20, defaults.register().max());
        assertEquals(20, defaults.forgotPasswordIp().max());
        assertEquals(5, defaults.forgotPasswordEmail().max());
        assertEquals(Duration.ofHours(1), defaults.forgotPasswordEmail().window());
        assertEquals(20, defaults.resetPassword().max());
        assertEquals(20, defaults.resendOtpIp().max());
        assertEquals(5, defaults.resendOtpEmail().max());
        assertEquals(60, defaults.verifyOtp().max());
        assertEquals(60, defaults.google().max());
        assertEquals(300, defaults.refresh().max());
        assertEquals(Duration.ofMinutes(15), defaults.refresh().window());
        // Global backstops: one shared allowance for all callers together, per 15 minutes.
        assertEquals(100, defaults.registerGlobal().max());
        assertEquals(60, defaults.forgotPasswordGlobal().max());
        assertEquals(100, defaults.resendOtpGlobal().max());
        assertEquals(60, defaults.resetPasswordGlobal().max());
        assertEquals(Duration.ofMinutes(15), defaults.forgotPasswordGlobal().window());
    }

    @Test
    void everyLimitCanBeChangedWithProperties() {
        RateLimitProperties tuned = bind(Map.of(
                "app.rate-limit.forgot-password-email.max", "2",
                "app.rate-limit.forgot-password-email.window", "30m",
                "app.rate-limit.refresh.max", "1000",
                "app.rate-limit.refresh.window", "1h"));
        assertEquals(2, tuned.forgotPasswordEmail().max());
        assertEquals(Duration.ofMinutes(30), tuned.forgotPasswordEmail().window());
        assertEquals(1000, tuned.refresh().max());
        assertEquals(Duration.ofHours(1), tuned.refresh().window());
        // Untouched ones keep their defaults.
        assertEquals(20, tuned.register().max());
    }

    @Test
    void aRuleWithOnlyOneHalfSetKeepsThatHalfAndDefaultsTheOther() {
        RateLimitProperties onlyMax = bind(Map.of("app.rate-limit.register.max", "3"));
        assertEquals(3, onlyMax.register().max());
        assertEquals(Duration.ofMinutes(15), onlyMax.register().window());

        RateLimitProperties onlyWindow = bind(Map.of("app.rate-limit.register.window", "1h"));
        assertEquals(20, onlyWindow.register().max());
        assertEquals(Duration.ofHours(1), onlyWindow.register().window());
    }

    // ------------------------------------------------------------------ behaviour

    @Test
    void registerHasItsOwnLimiterSeparateFromLogin() {
        RateLimitProperties props = bind(Map.of());
        LoginRateLimiter login = new LoginRateLimiter(props);
        AuthRateLimiters auth = new AuthRateLimiters(props, clock);

        // Use up the login allowance (5) for this address...
        callTimes(5, () -> login.enforce("203.0.113.7"));
        assertThrows(RateLimitExceededException.class, () -> login.enforce("203.0.113.7"));
        // ...registration from the same address is not affected, and has its own, larger allowance.
        callTimes(20, () -> auth.checkRegister("203.0.113.7"));
        assertThrows(RateLimitExceededException.class, () -> auth.checkRegister("203.0.113.7"));
    }

    @Test
    void theIpLimitOnForgotPasswordHoldsAcrossDifferentEmails() {
        AuthRateLimiters auth = limiters(Map.of());
        for (int i = 0; i < 20; i++) {
            auth.checkForgotPassword("203.0.113.7", "user" + i + "@example.test");
        }
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> auth.checkForgotPassword("203.0.113.7", "another@example.test"));
        assertNotNull(ex.getRetryAfterSeconds());
        assertTrue(ex.getRetryAfterSeconds() > 0 && ex.getRetryAfterSeconds() <= 15 * 60);
    }

    @Test
    void theEmailLimitOnForgotPasswordHoldsAcrossDifferentAddressesAndIgnoresCaseAndPadding() {
        AuthRateLimiters auth = limiters(Map.of());
        for (int i = 0; i < 5; i++) {
            auth.checkForgotPassword("198.51.100." + i, "victim@example.test");
        }
        for (String variant : new String[]{"VICTIM@example.test", "  victim@example.test ", "Victim@Example.Test"}) {
            RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                    () -> auth.checkForgotPassword("198.51.100.99", variant), variant);
            assertTrue(ex.getRetryAfterSeconds() <= 3600);
        }
        // A different person's email is unaffected.
        assertDoesNotThrow(() -> auth.checkForgotPassword("198.51.100.99", "someone-else@example.test"));
    }

    @Test
    void resendOtpHasPerIpAndPerEmailLimits() {
        AuthRateLimiters auth = limiters(Map.of());
        for (int i = 0; i < 5; i++) {
            auth.checkResendOtp("198.51.100." + i, "new-user@example.test");
        }
        assertThrows(RateLimitExceededException.class, () -> auth.checkResendOtp("198.51.100.77", "new-user@example.test"));

        AuthRateLimiters fresh = limiters(Map.of());
        for (int i = 0; i < 20; i++) {
            fresh.checkResendOtp("203.0.113.7", "u" + i + "@example.test");
        }
        assertThrows(RateLimitExceededException.class, () -> fresh.checkResendOtp("203.0.113.7", "u-new@example.test"));
    }

    @Test
    void anEmailBlockedByTheIpLimitDoesNotUseUpTheEmailAllowance() {
        AuthRateLimiters auth = limiters(Map.of("app.rate-limit.forgot-password-ip.max", "1"));
        auth.checkForgotPassword("203.0.113.7", "a@example.test");
        // Blocked by the IP limit: must not count against victim@'s allowance.
        for (int i = 0; i < 10; i++) {
            assertThrows(RateLimitExceededException.class, () -> auth.checkForgotPassword("203.0.113.7", "victim@example.test"));
        }
        for (int i = 0; i < 5; i++) {
            int n = i;
            assertDoesNotThrow(() -> auth.checkForgotPassword("198.51.100." + n, "victim@example.test"));
        }
    }

    @Test
    void theSimpleIpOnlyLimitsAllowTheirNumberThenBlock() {
        AuthRateLimiters auth = limiters(Map.of());
        callTimes(20, () -> auth.checkResetPassword("203.0.113.7"));
        assertThrows(RateLimitExceededException.class, () -> auth.checkResetPassword("203.0.113.7"));

        callTimes(60, () -> auth.checkVerifyOtp("203.0.113.7"));
        assertThrows(RateLimitExceededException.class, () -> auth.checkVerifyOtp("203.0.113.7"));

        callTimes(60, () -> auth.checkGoogle("203.0.113.7"));
        assertThrows(RateLimitExceededException.class, () -> auth.checkGoogle("203.0.113.7"));

        callTimes(300, () -> auth.checkRefresh("203.0.113.7"));
        assertThrows(RateLimitExceededException.class, () -> auth.checkRefresh("203.0.113.7"));
    }

    @Test
    void theAllowanceComesBackAfterTheWindow() {
        AuthRateLimiters auth = limiters(Map.of());
        callTimes(20, () -> auth.checkResetPassword("203.0.113.7"));
        assertThrows(RateLimitExceededException.class, () -> auth.checkResetPassword("203.0.113.7"));
        clock.advance(Duration.ofMinutes(16));
        assertDoesNotThrow(() -> auth.checkResetPassword("203.0.113.7"));
    }

    // ------------------------------------------------------------------ global backstop

    @Test
    void theGlobalCapStopsForgotPasswordAcrossAllAddressesAndEmails() {
        AuthRateLimiters auth = limiters(Map.of());
        // 60 requests, every one from a different address and for a different email: no per-IP or per-email limit applies.
        for (int i = 0; i < 60; i++) {
            auth.checkForgotPassword("198.51." + (i / 250) + "." + (i % 250 + 1), "user" + i + "@example.test");
        }
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> auth.checkForgotPassword("203.0.113.250", "one-more@example.test"));
        assertNotNull(ex.getRetryAfterSeconds());
        assertTrue(ex.getRetryAfterSeconds() > 0 && ex.getRetryAfterSeconds() <= 15 * 60);
        // ...and it clears when the window passes.
        clock.advance(Duration.ofMinutes(16));
        assertDoesNotThrow(() -> auth.checkForgotPassword("203.0.113.250", "one-more@example.test"));
    }

    @Test
    void everyEmailSendingOrTokenEndpointHasItsOwnGlobalCap() {
        AuthRateLimiters auth = limiters(Map.of());
        for (int i = 0; i < 100; i++) {
            String address = "198.51.100." + (i % 250 + 1);
            auth.checkRegister(address + "-r" + i);
            auth.checkResendOtp(address + "-o" + i, "p" + i + "@example.test");
        }
        assertThrows(RateLimitExceededException.class, () -> auth.checkRegister("x"));
        assertThrows(RateLimitExceededException.class, () -> auth.checkResendOtp("y", "q@example.test"));

        for (int i = 0; i < 60; i++) {
            auth.checkResetPassword("203.0.113." + (i % 250 + 1) + "-" + i);
        }
        assertThrows(RateLimitExceededException.class, () -> auth.checkResetPassword("z"));
    }

    @Test
    void theGlobalCapsAreIndependentOfEachOther() {
        AuthRateLimiters auth = limiters(Map.of());
        for (int i = 0; i < 60; i++) {
            auth.checkForgotPassword("203.0.113." + i, "u" + i + "@example.test");
        }
        assertThrows(RateLimitExceededException.class, () -> auth.checkForgotPassword("203.0.113.200", "v@example.test"));
        // Using up forgot-password's shared allowance does not touch the others.
        assertDoesNotThrow(() -> auth.checkResetPassword("203.0.113.201"));
        assertDoesNotThrow(() -> auth.checkResendOtp("203.0.113.202", "w@example.test"));
        assertDoesNotThrow(() -> auth.checkRegister("203.0.113.203"));
    }

    @Test
    void aBlockedByIpOrEmailRequestDoesNotUseUpTheGlobalAllowance() {
        AuthRateLimiters auth = limiters(Map.of("app.rate-limit.forgot-password-ip.max", "1", "app.rate-limit.forgot-password-ip.window", "1h"));
        auth.checkForgotPassword("203.0.113.7", "a@example.test");
        for (int i = 0; i < 500; i++) {
            assertThrows(RateLimitExceededException.class, () -> auth.checkForgotPassword("203.0.113.7", "b@example.test"));
        }
        // 59 more distinct callers still fit in the global allowance of 60 (one used above).
        for (int i = 0; i < 59; i++) {
            auth.checkForgotPassword("198.51.100." + (i + 1), "c" + i + "@example.test");
        }
    }

    @Test
    void normalBusyUseNeverComesNearTheGlobalCaps() {
        // A busy quarter of an hour for a small platform: 25 forgot-password, 30 OTP resends, 40 sign-ups, 15 resets,
        // each from a different person. All fit well inside 60 / 100 / 100 / 60.
        AuthRateLimiters auth = limiters(Map.of());
        for (int i = 0; i < 25; i++) {
            auth.checkForgotPassword("100.200.3." + i, "forgot" + i + "@example.test");
        }
        for (int i = 0; i < 30; i++) {
            auth.checkResendOtp("100.200.4." + i, "otp" + i + "@example.test");
        }
        for (int i = 0; i < 40; i++) {
            auth.checkRegister("100.200.5." + i);
        }
        for (int i = 0; i < 15; i++) {
            auth.checkResetPassword("100.200.6." + i);
        }
    }

    @Test
    void aGenerousSharedAddressCanStillDoNormalWork() {
        // 150 different people behind one carrier address each refreshing their session once in 15 minutes.
        AuthRateLimiters auth = limiters(Map.of());
        for (int i = 0; i < 150; i++) {
            auth.checkRefresh("100.200.1.1");
        }
    }
}
