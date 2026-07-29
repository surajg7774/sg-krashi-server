package com.sgkrashi.auth.dto.response;

import java.util.List;

/**
 * Shape returned by every auth endpoint that establishes or refreshes a session.
 * The refresh token is never included here — it travels only as an HttpOnly cookie.
 */
public record AuthResponse(String accessToken, UserSummary user) {

    public record UserSummary(Long id, String name, String email, List<String> roles) {
    }
}
