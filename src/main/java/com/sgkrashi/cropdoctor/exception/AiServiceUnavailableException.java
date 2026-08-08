package com.sgkrashi.cropdoctor.exception;

/**
 * Covers every way the Python AI service can fail to produce a usable
 * prediction — unreachable, timed out, non-2xx, or a malformed response
 * body. All map to the same client-facing {@code AI_SERVICE_UNAVAILABLE}
 * code (see GlobalExceptionHandler); the specific cause is only logged
 * server-side, since none of the distinctions are actionable for the caller.
 */
public class AiServiceUnavailableException extends RuntimeException {

    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
