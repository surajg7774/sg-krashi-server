package com.sgkrashi.auth.exception;

/** Thrown when {@code /auth/mobile/google} is hit before {@code GOOGLE_WEB_CLIENT_ID} has been set — same "boot fine, fail clearly only when actually used" contract as {@code MandiPriceApiClient.isConfigured()}. */
public class GoogleSignInNotConfiguredException extends RuntimeException {

    public GoogleSignInNotConfiguredException(String message) {
        super(message);
    }
}
