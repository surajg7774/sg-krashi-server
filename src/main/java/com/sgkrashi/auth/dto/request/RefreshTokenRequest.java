package com.sgkrashi.auth.dto.request;

/**
 * Body for {@code POST /api/v1/auth/mobile/refresh} ({@code MobileAuthController}).
 * The cookie-based {@code /api/v1/auth/refresh} ({@code AuthController}) still
 * reads the token via {@code @CookieValue} and never uses this record — a
 * native app has no HttpOnly cookie jar a server can rely on the way a
 * browser's is, so the mobile path carries the same raw refresh token here
 * in the body instead.
 */
public record RefreshTokenRequest(String refreshToken) {
}
