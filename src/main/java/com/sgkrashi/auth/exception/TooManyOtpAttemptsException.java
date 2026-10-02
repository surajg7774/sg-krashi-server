package com.sgkrashi.auth.exception;

/**
 * Thrown when a pending registration's OTP has been guessed wrong too many
 * times — the caller must request a fresh code via resend, not keep
 * guessing. Maps to HTTP 400.
 */
public class TooManyOtpAttemptsException extends RuntimeException {

    public TooManyOtpAttemptsException(String message) {
        super(message);
    }
}
