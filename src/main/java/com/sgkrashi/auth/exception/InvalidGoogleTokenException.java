package com.sgkrashi.auth.exception;

/** The Google ID token failed verification — bad signature, expired, wrong audience, or malformed. */
public class InvalidGoogleTokenException extends RuntimeException {

    public InvalidGoogleTokenException(String message) {
        super(message);
    }

    public InvalidGoogleTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
