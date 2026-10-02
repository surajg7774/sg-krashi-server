package com.sgkrashi.auth.exception;

/**
 * Thrown when a resend-OTP request arrives before the previous code's
 * cooldown window has elapsed. Maps to HTTP 429.
 */
public class OtpResendCooldownException extends RuntimeException {

    public OtpResendCooldownException(String message) {
        super(message);
    }
}
