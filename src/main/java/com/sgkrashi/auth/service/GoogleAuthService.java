package com.sgkrashi.auth.service;

import com.sgkrashi.auth.entity.User;

public interface GoogleAuthService {

    /**
     * Verifies the given Google ID token and resolves it to a {@code User}:
     * an existing account already linked by {@code googleId}, an existing
     * password-based account auto-linked by matching (Google-verified)
     * email, or a brand new account created with {@code passwordHash = null}.
     *
     * @throws com.sgkrashi.auth.exception.GoogleSignInNotConfiguredException if {@code GOOGLE_WEB_CLIENT_ID} is unset
     * @throws com.sgkrashi.auth.exception.InvalidGoogleTokenException if the token fails verification
     */
    User findOrCreateUser(String idToken);

    /**
     * Verifies the token and returns Google's stable subject id, creating or
     * linking nothing — used to re-confirm an already-logged-in Google account
     * before a destructive action (account deletion).
     *
     * @throws com.sgkrashi.auth.exception.GoogleSignInNotConfiguredException if {@code GOOGLE_WEB_CLIENT_ID} is unset
     * @throws com.sgkrashi.auth.exception.InvalidGoogleTokenException if the token fails verification
     */
    String verifyAndGetSubject(String idToken);
}
