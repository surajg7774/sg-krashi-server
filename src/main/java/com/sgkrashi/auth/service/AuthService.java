package com.sgkrashi.auth.service;

import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResendOtpRequest;
import com.sgkrashi.auth.dto.request.ResetPasswordRequest;
import com.sgkrashi.auth.dto.request.VerifyOtpRequest;

public interface AuthService {

    /**
     * Does NOT create a {@code User} row — only validates the request (email
     * not already taken) and emails a 6-digit OTP. The account is created by
     * {@link #verifyOtp}, only once that code is submitted correctly, which
     * is what guarantees every account belongs to an email the registrant
     * genuinely controls rather than just a string shaped like one.
     *
     * @throws com.sgkrashi.common.exception.DuplicateResourceException if the email is already registered
     */
    void register(RegisterRequest request);

    /**
     * Creates the account for a pending registration whose OTP matches and
     * hasn't expired, and logs it in — the only place a {@code User} row
     * actually gets created from a self-service registration.
     *
     * @throws com.sgkrashi.auth.exception.InvalidOtpException if the code is wrong, expired, or no pending registration exists for this email
     * @throws com.sgkrashi.auth.exception.TooManyOtpAttemptsException if too many wrong codes have been submitted
     * @throws com.sgkrashi.common.exception.DuplicateResourceException if this email was already verified by a concurrent request
     */
    AuthResult verifyOtp(VerifyOtpRequest request);

    /**
     * Regenerates and re-emails the OTP for a still-pending registration.
     *
     * @throws com.sgkrashi.auth.exception.InvalidOtpException if no pending registration exists for this email
     * @throws com.sgkrashi.auth.exception.OtpResendCooldownException if requested again before the cooldown window elapses
     */
    void resendOtp(ResendOtpRequest request);

    AuthResult login(LoginRequest request);

    /**
     * Verifies the given Google ID token and logs the resolved user in —
     * see {@link GoogleAuthService#findOrCreateUser} for the existing/
     * auto-linked/new-account resolution.
     *
     * @throws com.sgkrashi.auth.exception.GoogleSignInNotConfiguredException if Google Sign-In isn't configured yet
     * @throws com.sgkrashi.auth.exception.InvalidGoogleTokenException if the token fails verification
     */
    AuthResult loginWithGoogle(String idToken);

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
