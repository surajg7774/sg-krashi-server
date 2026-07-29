package com.sgkrashi.auth.dto.request;

/**
 * Present for API-completeness / OpenAPI documentation. In practice the refresh
 * token is read from the {@code refreshToken} HttpOnly cookie, never from the
 * request body, so this record is currently unused by {@code AuthController}.
 */
public record RefreshTokenRequest(String refreshToken) {
}
