package com.sgkrashi.auth.service;

import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResetPasswordRequest;

public interface AuthService {

    AuthResult register(RegisterRequest request);

    AuthResult login(LoginRequest request);

    /**
     * Validates the given raw refresh token, revokes it, and issues a fresh
     * access token plus a rotated refresh token.
     *
     * @throws com.sgkrashi.common.exception.InvalidTokenException if the token is missing, expired, or revoked
     */
    AuthResult refresh(String rawRefreshToken);

    /**
     * Revokes the given raw refresh token, if any. Never throws for an absent
     * or already-invalid token — logout always succeeds from the caller's perspective.
     */
    void logout(String rawRefreshToken);

    /**
     * Always completes successfully regardless of whether the email is registered,
     * to avoid revealing account existence.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * @throws com.sgkrashi.common.exception.InvalidTokenException if the reset token is missing, expired, or invalid
     */
    void resetPassword(ResetPasswordRequest request);
}
