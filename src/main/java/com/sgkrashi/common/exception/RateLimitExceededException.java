package com.sgkrashi.common.exception;

/**
 * Thrown when a caller exceeds an endpoint's allowed request rate. Maps to HTTP 429.
 * When {@link #getRetryAfterSeconds()} is known it is sent as the {@code Retry-After} header.
 */
public class RateLimitExceededException extends RuntimeException {

    /** Seconds until the caller may try again, or {@code null} when not known. */
    private final Long retryAfterSeconds;

    public RateLimitExceededException(String message) {
        super(message);
        this.retryAfterSeconds = null;
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
