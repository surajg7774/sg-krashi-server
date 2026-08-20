package com.sgkrashi.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * {@code gemini-embedding-001}, not the newer {@code gemini-embedding-2} —
 * deliberately, because 001 is the one that still supports the {@code
 * taskType} parameter (per Google's own embeddings documentation, {@code
 * gemini-embedding-2} dropped it in favor of describing the task in the
 * prompt text itself). {@code taskType} matters for both this class's
 * callers (AI Crop Doctor RAG, the chat assistant's platform knowledge
 * retrieval): both are asymmetric retrieval problems — the knowledge base
 * holds multi-paragraph documents, the query is short. Using the wrong task
 * type for either side is a well-documented way to quietly hurt retrieval
 * quality.
 *
 * <p>{@code outputDimensionality=768}, not the 3072 default: Matryoshka
 * Representation Learning (which this model uses) means a truncated prefix
 * of the full vector stays meaningfully accurate, and at the scale either
 * caller's knowledge base actually operates at (a few dozen entries, no
 * vector index) smaller vectors just mean less to store and less arithmetic
 * per cosine-similarity comparison, for a quality tradeoff Google's own docs
 * describe as minimal at this size.
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int OUTPUT_DIMENSIONALITY = 768;

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public EmbeddingServiceImpl(
            @Value("${app.gemini.api-key}") String apiKey,
            @Value("${app.gemini.embedding-model:gemini-embedding-001}") String model,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public float[] embedDocument(String text) {
        return embed(text, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(text, "RETRIEVAL_QUERY");
    }

    private float[] embed(String text, String taskType) {
        ObjectNode textPart = objectMapper.createObjectNode().put("text", text);
        ObjectNode content = objectMapper.createObjectNode();
        content.set("parts", objectMapper.createArrayNode().add(textPart));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("content", content);
        payload.put("taskType", taskType);
        payload.put("outputDimensionality", OUTPUT_DIMENSIONALITY);

        try {
            String responseBody = webClient.post()
                    .uri("/v1beta/models/{model}:embedContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            EmbeddingApiResponse parsed = objectMapper.readValue(responseBody, EmbeddingApiResponse.class);
            return parsed.embedding().values();
        } catch (WebClientResponseException ex) {
            log.warn("Gemini embedding API returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(),
                    ex.getResponseBodyAsString());
            throw new EmbeddingUnavailableException("Embedding API returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("Gemini embedding API unreachable: {}", ex.getMessage());
            throw new EmbeddingUnavailableException("Embedding API is unreachable", ex);
        } catch (Exception ex) {
            log.warn("Gemini embedding API call failed", ex);
            throw new EmbeddingUnavailableException("Embedding API call failed", ex);
        }
    }
}
