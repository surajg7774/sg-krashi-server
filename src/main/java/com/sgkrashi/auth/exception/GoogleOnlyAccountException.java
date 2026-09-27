package com.sgkrashi.auth.exception;

/**
 * Thrown by {@code CustomUserDetailsService} when a password-login attempt
 * targets an account that has {@code password_hash = NULL} — a Google-only
 * account (see {@code User}'s Javadoc). Deliberately more specific than the
 * generic "invalid email or password" — unlike a genuinely wrong password,
 * telling this user "use Google Sign-In instead" is actionable, not a
 * meaningful account-enumeration leak (registration's duplicate-email check
 * already reveals whether an email is registered at all).
 */
public class GoogleOnlyAccountException extends RuntimeException {

    public GoogleOnlyAccountException(String message) {
        super(message);
    }
}
