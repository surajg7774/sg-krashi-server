package com.sgkrashi.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The global backstop, over real HTTP with the DEFAULT numbers (its own application context, so no other test
 * has used up the shared allowance): even if every request carries a different, forged client address and a
 * different email, so that neither the per-IP nor the per-email limit can ever trigger, the total number of
 * password-reset requests is still capped, and so is the email that can follow from them.
 */
@SpringBootTest(classes = ClientIpResolutionTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // A different property set gives this class its own context (and its own limiter state).
        properties = {"app.rate-limit.reset-password-global.max=60"})
@ActiveProfiles("prod")
class GlobalBackstopTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String body, String forgedAddress) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Real-IP", forgedAddress)
                .header("X-Forwarded-For", forgedAddress)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String address(int i) {
        return "198.51." + (i / 250) + "." + (i % 250 + 1);
    }

    @Test
    void forgotPasswordIsCappedAcrossAllCallersEvenWithForgedAddressesAndDifferentEmails() throws Exception {
        int ok = 0;
        HttpResponse<String> firstBlocked = null;
        for (int i = 1; i <= 80; i++) {
            HttpResponse<String> response = post("/api/v1/auth/forgot-password",
                    "{\"email\":\"user" + i + "@example.test\"}", address(i));
            if (response.statusCode() == 200) {
                ok++;
            } else if (firstBlocked == null) {
                firstBlocked = response;
            }
        }
        assertEquals(60, ok, "default global cap for forgot-password is 60 per 15 minutes");
        assertNotNull(firstBlocked);
        assertEquals(429, firstBlocked.statusCode());
        assertTrue(Long.parseLong(firstBlocked.headers().firstValue("Retry-After").orElse("0")) > 0, "Retry-After");
    }

    @Test
    void resetPasswordIsCappedAcrossAllCallersToo() throws Exception {
        int ok = 0;
        for (int i = 1; i <= 80; i++) {
            HttpResponse<String> response = post("/api/v1/auth/reset-password",
                    "{\"token\":\"t" + i + "\",\"newPassword\":\"password123\"}", address(1000 + i));
            if (response.statusCode() == 200) {
                ok++;
            }
        }
        assertEquals(60, ok);
    }

    @Test
    void resendOtpAndRegisterHaveTheirOwnGlobalCaps() throws Exception {
        int resendOk = 0;
        for (int i = 1; i <= 120; i++) {
            if (post("/api/v1/auth/resend-otp", "{\"email\":\"pending" + i + "@example.test\"}", address(2000 + i)).statusCode() == 200) {
                resendOk++;
            }
        }
        assertEquals(100, resendOk, "default global cap for resend-otp is 100 per 15 minutes");

        int registerOk = 0;
        for (int i = 1; i <= 120; i++) {
            String body = "{\"name\":\"N\",\"email\":\"reg" + i + "@example.test\",\"password\":\"password123\"}";
            if (post("/api/v1/auth/register", body, address(3000 + i)).statusCode() == 200) {
                registerOk++;
            }
        }
        assertEquals(100, registerOk, "default global cap for register is 100 per 15 minutes");
    }
}
