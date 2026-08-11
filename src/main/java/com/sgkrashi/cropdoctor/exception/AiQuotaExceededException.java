package com.sgkrashi.cropdoctor.exception;

/**
 * The AI provider's own quota (e.g. Gemini's daily free-tier cap) is
 * exhausted — distinct from {@link AiServiceUnavailableException} (transient
 * connection/response failure) and from our own per-user
 * {@code RateLimitExceededException}, since the user-facing message differs:
 * this is "come back tomorrow," not "slow down" or "try again shortly."
 */
public class AiQuotaExceededException extends RuntimeException {

    public AiQuotaExceededException(String message) {
        super(message);
    }

    public AiQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
