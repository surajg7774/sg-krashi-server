package com.sgkrashi.auth.dto.response;

/**
 * Mobile counterpart to {@link AuthResponse} — same login/verify-email
 * response, plus the refresh token in the body instead of an HttpOnly
 * cookie. Exists because a native app has no cookie jar a server can rely
 * on the way a browser's is relied on by {@code AuthController}'s
 * {@code SameSite=None} cookie; see {@code MobileAuthController}'s own
 * Javadoc for the full reasoning. Never returned by the existing
 * {@code /auth/login} or {@code /auth/verify-email} — those keep returning
 * bare {@link AuthResponse}, unchanged.
 */
public record MobileAuthResponse(String accessToken, String refreshToken, AuthResponse.UserSummary user) {
}
