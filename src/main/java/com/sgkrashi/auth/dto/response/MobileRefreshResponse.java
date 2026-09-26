package com.sgkrashi.auth.dto.response;

/**
 * Response for {@code POST /api/v1/auth/mobile/refresh} — the rotated
 * access/refresh pair, both in the body. No {@code user} field: unlike
 * {@link MobileAuthResponse} (returned by mobile login), a refresh call
 * already has an authenticated caller who isn't re-fetching their own
 * profile, matching what the existing cookie-based {@code /auth/refresh}
 * returns today (just a fresh {@code AuthResponse}, no re-sent user
 * changes beyond what's already in {@code AuthResponse.user} there either).
 */
public record MobileRefreshResponse(String accessToken, String refreshToken) {
}
