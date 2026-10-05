package com.sgkrashi.mandi.scheduler;

import com.sgkrashi.mandi.client.MandiApiUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MandiSyncFailureTest {

    private static MandiApiUnavailableException network(Throwable cause) {
        return new MandiApiUnavailableException("Mandi price API is unreachable",
                new WebClientRequestException(cause, HttpMethod.GET, URI.create("https://api.data.gov.in/resource/x?api-key=SECRET"), HttpHeaders.EMPTY));
    }

    @Test
    void connectionRefused() {
        assertEquals("unreachable (connection refused)", MandiSyncFailure.describe(network(
                new ConnectException("finishConnect(..) failed with error(-111): Connection refused: api.data.gov.in/164.100.61.198:443"))));
    }

    @Test
    void readOrConnectTimeout() {
        assertEquals("unreachable (timed out)", MandiSyncFailure.describe(network(new SocketTimeoutException("Read timed out"))));
    }

    @Test
    void reactorTimeoutThatIsNotWrappedInARequestException() {
        MandiApiUnavailableException ex = new MandiApiUnavailableException("Mandi price API call failed", new TimeoutException("Did not observe any item"));
        assertEquals("unreachable (timed out)", MandiSyncFailure.describe(ex));
    }

    @Test
    void dnsFailure() {
        assertEquals("unreachable (DNS lookup failed)", MandiSyncFailure.describe(network(new UnknownHostException("api.data.gov.in"))));
    }

    @Test
    void tlsFailure() {
        assertEquals("unreachable (TLS error)", MandiSyncFailure.describe(network(new SSLHandshakeException("Remote host terminated the handshake"))));
    }

    @Test
    void anErrorResponseIsReportedByStatusCode() {
        WebClientResponseException response = WebClientResponseException.create(503, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
        assertEquals("HTTP 503", MandiSyncFailure.describe(new MandiApiUnavailableException("Mandi price API returned an error response", response)));
    }

    @Test
    void anUnparseableBody() {
        assertEquals("invalid response", MandiSyncFailure.describe(new MandiApiUnavailableException("Mandi price API returned an invalid response", new IllegalStateException("bad json"))));
    }

    @Test
    void anythingElseIsJustTheClassName() {
        assertEquals("failed (IllegalStateException)", MandiSyncFailure.describe(new MandiApiUnavailableException("Mandi price API call failed", new IllegalStateException("boom, with api-key=SECRET in it"))));
    }
}
