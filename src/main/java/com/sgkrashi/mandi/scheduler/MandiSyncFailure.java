package com.sgkrashi.mandi.scheduler;

import com.sgkrashi.mandi.client.MandiApiUnavailableException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Turns a failed data.gov.in call into one short, greppable reason for the
 * sync log line. Maps to fixed categories (or a bare class name) rather than
 * echoing exception messages: those can carry the request URL, and the URL
 * carries the API key.
 */
final class MandiSyncFailure {

    private MandiSyncFailure() {
    }

    static String describe(MandiApiUnavailableException ex) {
        Throwable cause = ex.getCause();

        if (cause instanceof WebClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        if (cause instanceof WebClientRequestException) {
            return "unreachable (" + networkCategory(cause) + ")";
        }
        if (hasTimeout(cause)) {
            return "unreachable (timed out)";
        }
        if (ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("invalid response")) {
            return "invalid response";
        }
        return "failed (" + (cause != null ? cause.getClass().getSimpleName() : "unknown") + ")";
    }

    private static String networkCategory(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String text = (t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage())).toLowerCase(Locale.ROOT);
            if (text.contains("connection refused")) {
                return "connection refused";
            }
            if (text.contains("timeout") || text.contains("timed out")) {
                return "timed out";
            }
            if (text.contains("unknownhost") || text.contains("failed to resolve") || text.contains("name or service not known")) {
                return "DNS lookup failed";
            }
            if (text.contains("ssl") || text.contains("handshake") || text.contains("certificate")) {
                return "TLS error";
            }
            if (text.contains("connection reset")) {
                return "connection reset";
            }
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private static boolean hasTimeout(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
