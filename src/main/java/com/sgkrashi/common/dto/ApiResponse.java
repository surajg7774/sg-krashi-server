package com.sgkrashi.common.dto;

import java.time.Instant;

/**
 * Generic envelope for all successful API responses.
 */
public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }
}
