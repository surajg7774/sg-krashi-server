package com.sgkrashi.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits for the authentication endpoints ({@code app.rate-limit.*}). Every value has a default,
 * so no environment variable is needed; set the property to change one. Counts are per window
 * and per key (client IP, or normalised email address for the {@code *Email} limits).
 *
 * <p>The defaults are deliberately generous for the per-IP limits, because many real users share
 * one IP behind a mobile carrier's NAT; the per-email limits are the ones that stop one victim's
 * inbox being flooded. {@code login} keeps its original 5 attempts per 15 minutes.
 *
 * <p>The {@code *Global} rules are one shared allowance for ALL callers together on the endpoints that
 * send email or can be hammered: they are the backstop that still bounds email volume (and the Brevo quota)
 * if the per-IP key is ever forged. Their keys cannot be chosen by a caller, so they cannot be dodged;
 * the price is that a flood can use the shared allowance up for a while and make other callers wait.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue Rule login,
        @DefaultValue Rule register,
        @DefaultValue Rule forgotPasswordIp,
        @DefaultValue Rule forgotPasswordEmail,
        @DefaultValue Rule resetPassword,
        @DefaultValue Rule resendOtpIp,
        @DefaultValue Rule resendOtpEmail,
        @DefaultValue Rule verifyOtp,
        @DefaultValue Rule google,
        @DefaultValue Rule refresh,
        @DefaultValue Rule registerGlobal,
        @DefaultValue Rule forgotPasswordGlobal,
        @DefaultValue Rule resendOtpGlobal,
        @DefaultValue Rule resetPasswordGlobal
) {

    /** One limit: {@code max} requests per {@code window}. */
    public record Rule(@DefaultValue("0") int max, @DefaultValue("0s") Duration window) {
    }

    /** The rule to use: each half that was set is kept, a half that was not set takes the built-in default. */
    static Rule orDefault(Rule configured, int defaultMax, Duration defaultWindow) {
        int max = configured != null && configured.max() >= 1 ? configured.max() : defaultMax;
        Duration window = configured != null && configured.window() != null && !configured.window().isZero()
                ? configured.window() : defaultWindow;
        return new Rule(max, window);
    }

    public Rule login() {
        return orDefault(login, 5, Duration.ofMinutes(15));
    }

    public Rule register() {
        return orDefault(register, 20, Duration.ofMinutes(15));
    }

    public Rule forgotPasswordIp() {
        return orDefault(forgotPasswordIp, 20, Duration.ofMinutes(15));
    }

    public Rule forgotPasswordEmail() {
        return orDefault(forgotPasswordEmail, 5, Duration.ofHours(1));
    }

    public Rule resetPassword() {
        return orDefault(resetPassword, 20, Duration.ofMinutes(15));
    }

    public Rule resendOtpIp() {
        return orDefault(resendOtpIp, 20, Duration.ofMinutes(15));
    }

    public Rule resendOtpEmail() {
        return orDefault(resendOtpEmail, 5, Duration.ofHours(1));
    }

    public Rule verifyOtp() {
        return orDefault(verifyOtp, 60, Duration.ofMinutes(15));
    }

    public Rule google() {
        return orDefault(google, 60, Duration.ofMinutes(15));
    }

    public Rule refresh() {
        return orDefault(refresh, 300, Duration.ofMinutes(15));
    }

    public Rule registerGlobal() {
        return orDefault(registerGlobal, 100, Duration.ofMinutes(15));
    }

    public Rule forgotPasswordGlobal() {
        return orDefault(forgotPasswordGlobal, 60, Duration.ofMinutes(15));
    }

    public Rule resendOtpGlobal() {
        return orDefault(resendOtpGlobal, 100, Duration.ofMinutes(15));
    }

    public Rule resetPasswordGlobal() {
        return orDefault(resetPasswordGlobal, 60, Duration.ofMinutes(15));
    }
}
