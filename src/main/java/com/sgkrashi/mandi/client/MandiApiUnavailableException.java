package com.sgkrashi.mandi.client;

/** The data.gov.in call itself failed (network, timeout, error response, or an unparseable body) — a real key was configured, the call just didn't succeed this run. */
public class MandiApiUnavailableException extends MandiApiException {

    public MandiApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
