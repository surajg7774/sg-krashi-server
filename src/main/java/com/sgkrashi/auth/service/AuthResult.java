package com.sgkrashi.auth.service;

import com.sgkrashi.auth.dto.response.AuthResponse;

/**
 * Internal carrier from the service layer to {@code AuthController}: the JSON
 * body ({@link AuthResponse}) plus the raw refresh token, which the controller
 * sets as an HttpOnly cookie and never includes in the response body.
 */
public record AuthResult(AuthResponse response, String rawRefreshToken) {
}
