package com.sgkrashi.auth.exception;

/**
 * Thrown when a submitted OTP is wrong, expired, or no pending registration
 * exists for the given email. Maps to HTTP 400.
 */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException(String message) {
        super(message);
    }
}
