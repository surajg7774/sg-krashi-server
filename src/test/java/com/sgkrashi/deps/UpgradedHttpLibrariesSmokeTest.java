package com.sgkrashi.deps;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The libraries whose versions were overridden for security fixes must still work together at run time, not
 * just compile: Cloudinary's SDK runs on httpclient5 + httpcore5 (bumped as a matched pair), Razorpay's SDK
 * runs on okhttp (3.10.0 -> 4.12.0). Tomcat, Jackson and Netty are exercised by the embedded-server and
 * WebClient tests elsewhere in this suite.
 */
class UpgradedHttpLibrariesSmokeTest {

    @Test
    void cloudinaryUploadWorksOverTheUpgradedHttpClient() throws Exception {
        AtomicReference<String> requestLine = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestLine.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] reply = ("{\"public_id\":\"sample\",\"version\":1,\"resource_type\":\"image\",\"format\":\"png\","
                    + "\"secure_url\":\"https://res.example.test/sample.png\",\"url\":\"http://res.example.test/sample.png\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        try {
            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", "testcloud", "api_key", "123456", "api_secret", "not-a-real-secret",
                    "upload_prefix", "http://127.0.0.1:" + server.getAddress().getPort()));

            Map<?, ?> result = cloudinary.uploader().upload(new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}, ObjectUtils.emptyMap());

            assertEquals("POST /v1_1/testcloud/image/upload", requestLine.get());
            assertTrue(contentType.get().startsWith("multipart/form-data"), contentType.get());
            assertTrue(body.get().contains("name=\"signature\""), "upload must be signed");
            assertEquals("sample", result.get("public_id"));
            assertEquals("https://res.example.test/sample.png", result.get("secure_url"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * Sends one request to the real api.razorpay.com with obviously invalid credentials, so it needs the
     * internet and is off by default ({@code -Drazorpay.smoke=true}). Nothing can be created: Razorpay answers
     * with an authentication error, which still proves the okhttp 4 TLS request/response path inside the SDK.
     */
    @Test
    @EnabledIfSystemProperty(named = "razorpay.smoke", matches = "true")
    void razorpaySdkRunsOnOkHttp4() throws Exception {
        RazorpayClient client = new RazorpayClient("invalid-key-id-for-smoke-test", "invalid-secret-for-smoke-test");
        JSONObject request = new JSONObject().put("amount", 100).put("currency", "INR").put("receipt", "smoke");
        try {
            client.orders.create(request);
            throw new AssertionError("Razorpay accepted invalid credentials?");
        } catch (RazorpayException expected) {
            assertNotNull(expected.getMessage());
            String message = expected.getMessage().toLowerCase();
            assertTrue(message.contains("authentication") || message.contains("auth") || message.contains("key"),
                    "expected an authentication error from Razorpay, got: " + expected.getMessage());
        }
    }
}
