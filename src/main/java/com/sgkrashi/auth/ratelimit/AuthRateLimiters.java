package com.sgkrashi.auth.ratelimit;

import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.common.ratelimit.FixedWindowRateLimiter;
import com.sgkrashi.common.ratelimit.RateLimitProperties;
import com.sgkrashi.common.ratelimit.RateLimitProperties.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;

/**
 * Rate limits for the account endpoints that send email or accept a code or token: registration,
 * forgot/reset password, OTP resend/verify, Google sign-in and token refresh. Login keeps its own
 * {@link LoginRateLimiter}. Each check throws {@link RateLimitExceededException} (HTTP 429 with a
 * {@code Retry-After} header) when exceeded. Numbers come from {@link RateLimitProperties}.
 *
 * <p>Three layers, checked in this order: per IP (generous, because many real users share one IP behind
 * a carrier's NAT), per email (stops one victim's inbox being flooded, even from many IPs), and a GLOBAL
 * cap per endpoint across all callers. The global cap is the backstop for when the per-IP key can be
 * forged: it bounds how many emails (and how much of the Brevo quota) these endpoints can cause in total.
 * Trade-offs: someone can use up a victim's per-email allowance and so delay that person's own reset email
 * for up to the window, and a large enough flood can use the global allowance up so that everyone waits.
 */
@Component
public class AuthRateLimiters {

    private static final String MESSAGE = "Too many attempts. Please try again in a few minutes.";
    private static final String GLOBAL_KEY = "all-callers";

    private final FixedWindowRateLimiter register;
    private final FixedWindowRateLimiter forgotPasswordIp;
    private final FixedWindowRateLimiter forgotPasswordEmail;
    private final FixedWindowRateLimiter resetPassword;
    private final FixedWindowRateLimiter resendOtpIp;
    private final FixedWindowRateLimiter resendOtpEmail;
    private final FixedWindowRateLimiter verifyOtp;
    private final FixedWindowRateLimiter google;
    private final FixedWindowRateLimiter refresh;
    private final FixedWindowRateLimiter registerGlobal;
    private final FixedWindowRateLimiter forgotPasswordGlobal;
    private final FixedWindowRateLimiter resendOtpGlobal;
    private final FixedWindowRateLimiter resetPasswordGlobal;

    @Autowired
    public AuthRateLimiters(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRateLimiters(RateLimitProperties properties, Clock clock) {
        this.register = limiter(properties.register(), clock);
        this.forgotPasswordIp = limiter(properties.forgotPasswordIp(), clock);
        this.forgotPasswordEmail = limiter(properties.forgotPasswordEmail(), clock);
        this.resetPassword = limiter(properties.resetPassword(), clock);
        this.resendOtpIp = limiter(properties.resendOtpIp(), clock);
        this.resendOtpEmail = limiter(properties.resendOtpEmail(), clock);
        this.verifyOtp = limiter(properties.verifyOtp(), clock);
        this.google = limiter(properties.google(), clock);
        this.refresh = limiter(properties.refresh(), clock);
        this.registerGlobal = limiter(properties.registerGlobal(), clock);
        this.forgotPasswordGlobal = limiter(properties.forgotPasswordGlobal(), clock);
        this.resendOtpGlobal = limiter(properties.resendOtpGlobal(), clock);
        this.resetPasswordGlobal = limiter(properties.resetPasswordGlobal(), clock);
    }

    private static FixedWindowRateLimiter limiter(Rule rule, Clock clock) {
        return new FixedWindowRateLimiter(rule.max(), rule.window(), clock, FixedWindowRateLimiter.DEFAULT_MAX_KEYS);
    }

    public void checkRegister(String clientKey) {
        enforce(register, clientKey);
        enforce(registerGlobal, GLOBAL_KEY);
    }

    public void checkForgotPassword(String clientKey, String email) {
        enforce(forgotPasswordIp, clientKey);
        enforce(forgotPasswordEmail, normaliseEmail(email));
        enforce(forgotPasswordGlobal, GLOBAL_KEY);
    }

    public void checkResetPassword(String clientKey) {
        enforce(resetPassword, clientKey);
        enforce(resetPasswordGlobal, GLOBAL_KEY);
    }

    public void checkResendOtp(String clientKey, String email) {
        enforce(resendOtpIp, clientKey);
        enforce(resendOtpEmail, normaliseEmail(email));
        enforce(resendOtpGlobal, GLOBAL_KEY);
    }

    public void checkVerifyOtp(String clientKey) {
        enforce(verifyOtp, clientKey);
    }

    public void checkGoogle(String clientKey) {
        enforce(google, clientKey);
    }

    public void checkRefresh(String clientKey) {
        enforce(refresh, clientKey);
    }

    private static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void enforce(FixedWindowRateLimiter limiter, String key) {
        FixedWindowRateLimiter.Decision decision = limiter.acquire(key);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(MESSAGE, decision.retryAfterSeconds());
        }
    }
}
