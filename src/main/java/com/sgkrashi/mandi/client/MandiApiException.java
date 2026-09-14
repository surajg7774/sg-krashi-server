package com.sgkrashi.mandi.client;

/** Base type for anything that stops a mandi price sync from completing — the sync job always catches this, never lets it propagate. */
public class MandiApiException extends RuntimeException {

    public MandiApiException(String message) {
        super(message);
    }

    public MandiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
