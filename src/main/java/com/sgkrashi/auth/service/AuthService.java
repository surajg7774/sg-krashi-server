package com.sgkrashi.auth.service;

import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResetPasswordRequest;
import com.sgkrashi.auth.dto.request.VerifyEmailRequest;

public interface AuthService {

    /**
     * Does NOT create a {@code User} row — only validates the request (email
     * not already taken) and emails a verification link. The account is
     * created by {@link #verifyEmail}, only once that link is actually
     * clicked, which is what guarantees every account belongs to an email
     * the registrant genuinely controls rather than just a string shaped
     * like one.
     *
     * @throws com.sgkrashi.common.exception.DuplicateResourceException if the email is already registered
     */
    void register(RegisterRequest request);

    /**
     * Creates the account embedded in a valid, unexpired verification token
     * and logs it in — the only place a {@code User} row actually gets
     * created from a self-service registration.
     *
     * @throws com.sgkrashi.common.exception.InvalidTokenException if the token is missing, expired, or invalid
     * @throws com.sgkrashi.common.exception.DuplicateResourceException if this email was already verified (e.g. the link was clicked twice)
     */
    AuthResult verifyEmail(VerifyEmailRequest request);

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
