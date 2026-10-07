package com.sgkrashi.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.ai.embedding.EmbeddingServiceImpl;
import com.sgkrashi.ai.weather.service.WeatherService;
import com.sgkrashi.chatassistant.provider.ChatTurn;
import com.sgkrashi.chatassistant.provider.GeminiChatProvider;
import com.sgkrashi.cropdoctor.provider.gemini.GeminiAnalysisProvider;
import com.sgkrashi.cropdoctor.rag.service.RetrievalService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * All three Gemini callers (embeddings, chat, crop-doctor analysis) must send the API key
 * in the {@code x-goog-api-key} header, never as {@code ?key=} in the URL, and the key must
 * not show up in anything logged when the call fails. A local HTTP server stands in for
 * Gemini and answers 500, which drives each caller down its error-logging path.
 */
class GeminiApiKeyHeaderTest {

    private static final String KEY = "TEST-KEY-do-not-use-0123456789abcdef";

    private record Seen(String method, String path, String rawQuery, String keyHeader) {
    }

    private HttpServer server;
    private String baseUrl;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void startFakeGemini() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst(GeminiApiKey.HEADER)));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void stop() {
        rootLogger.detachAppender(logs);
        logs.stop();
        server.stop(0);
    }

    private void assertKeyInHeaderOnly(String expectedPathSuffix) {
        assertEquals(1, seen.size(), "exactly one request expected");
        Seen request = seen.get(0);
        assertTrue(request.path().endsWith(expectedPathSuffix), request.path());
        assertNull(request.rawQuery(), "no query string at all: the key must not be in the URL");
        assertEquals(KEY, request.keyHeader());
        assertFalse(request.path().contains(KEY));
    }

    private void assertKeyNotLogged() {
        for (ILoggingEvent event : logs.list) {
            String everything = event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy()));
            assertFalse(everything.contains(KEY), "API key leaked into a log line: " + event.getLoggerName());
        }
        assertFalse(logs.list.isEmpty(), "the failure path should have logged something for this check to mean anything");
    }

    @Test
    void embeddingsSendTheKeyInTheHeader() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(KEY, "test-embed", baseUrl, new ObjectMapper());
        try {
            service.embedQuery("tomato blight");
        } catch (RuntimeException expected) {
            // 500 from the fake server
        }
        assertKeyInHeaderOnly(":embedContent");
        assertKeyNotLogged();
    }

    @Test
    void chatSendsTheKeyInTheHeader() {
        GeminiChatProvider provider = new GeminiChatProvider(KEY, "test-chat", baseUrl, new ObjectMapper());
        try {
            provider.reply(List.of(new ChatTurn("user", "hi")), "hello", null, null, false, null);
        } catch (RuntimeException expected) {
        }
        assertKeyInHeaderOnly(":generateContent");
        assertKeyNotLogged();
    }

    @Test
    void cropDoctorAnalysisSendsTheKeyInTheHeader() {
        RetrievalService retrieval = mock(RetrievalService.class);
        when(retrieval.retrieveForCrop("Tomato", 3)).thenReturn(List.of());
        WeatherService weather = mock(WeatherService.class);
        when(weather.fetchCurrentWeather()).thenReturn(Optional.empty());
        GeminiAnalysisProvider provider =
                new GeminiAnalysisProvider(KEY, "test-model", baseUrl, new ObjectMapper(), retrieval, weather);
        MockMultipartFile image = new MockMultipartFile("files", "leaf.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        try {
            provider.analyze(List.of(image), "Tomato", "en");
        } catch (RuntimeException expected) {
        }
        assertFalse(seen.isEmpty());
        Seen request = seen.get(0);
        assertTrue(request.path().endsWith(":generateContent"), request.path());
        assertNull(request.rawQuery());
        assertEquals(KEY, request.keyHeader());
        assertKeyNotLogged();
    }
}
