package com.sgkrashi.common.exception;

/**
 * Thrown when a caller exceeds an endpoint's allowed request rate. Maps to HTTP 429.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
