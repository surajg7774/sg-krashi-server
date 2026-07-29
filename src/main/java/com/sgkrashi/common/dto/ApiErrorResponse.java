package com.sgkrashi.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Generic envelope for all error API responses.
 */
public record ApiErrorResponse(boolean success, ApiError error, Instant timestamp) {

    public static ApiErrorResponse of(String code, String message, List<String> details) {
        return new ApiErrorResponse(false, new ApiError(code, message, details), Instant.now());
    }

    public record ApiError(String code, String message, List<String> details) {
    }
}
