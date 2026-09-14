package com.sgkrashi.mandi.client;

/** Thrown when {@code app.mandi.api-key} is blank — expected and routine until a real data.gov.in key is registered, never logged as an error. */
public class MandiApiNotConfiguredException extends MandiApiException {

    public MandiApiNotConfiguredException(String message) {
        super(message);
    }
}
