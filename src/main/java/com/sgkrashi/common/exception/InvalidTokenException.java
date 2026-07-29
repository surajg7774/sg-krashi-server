package com.sgkrashi.common.exception;

/**
 * Thrown when a refresh token or password-reset token is missing, expired,
 * revoked, or otherwise fails validation. Maps to HTTP 401.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
