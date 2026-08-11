package com.sgkrashi.cropdoctor.service.impl;

import com.sgkrashi.cropdoctor.dto.response.AiPredictionResponse;
import com.sgkrashi.cropdoctor.dto.response.RawAiPredictionResponse;
import com.sgkrashi.cropdoctor.exception.AiServiceUnavailableException;
import com.sgkrashi.cropdoctor.service.AiServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * The HTTP boundary to the Python AI service (Section 3 of the feature
 * spec). No existing outbound-HTTP-client convention exists in this project
 * to follow (Razorpay integration goes through its own SDK, not a generic
 * client) — WebClient is used here, called synchronously via {@code .block()}
 * since this is a simple request/response flow, not a streaming one.
 *
 * <p>Phase 1 (Gemini) demoted this to the disabled-by-default local
 * fallback (Section 3.7 of the Phase 1 spec) — {@code @ConditionalOnProperty}
 * means this bean, and its required {@code AI_SERVICE_URL}/{@code
 * AI_SERVICE_API_KEY} env vars, are only ever needed if {@code
 * crop-doctor.provider=local} is explicitly set. With Gemini active (the
 * default), those env vars can be safely left unset — this bean is never
 * constructed, so their absence never triggers a startup failure.
 */
@Component
@ConditionalOnProperty(prefix = "crop-doctor", name = "provider", havingValue = "local")
public class AiServiceClientImpl implements AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClientImpl.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final String apiKey;

    public AiServiceClientImpl(
            @Value("${app.ai-crop-doctor.service-url}") String serviceUrl,
            @Value("${app.ai-crop-doctor.api-key}") String apiKey
    ) {
        this.webClient = WebClient.builder().baseUrl(serviceUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public AiPredictionResponse predict(MultipartFile file) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", file.getResource())
                .contentType(MediaType.parseMediaType(file.getContentType()));

        RawAiPredictionResponse raw;
        try {
            raw = webClient.post()
                    .uri("/predict")
                    .header("X-Internal-Api-Key", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(RawAiPredictionResponse.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("AI service returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(),
                    ex.getResponseBodyAsString());
            throw new AiServiceUnavailableException("AI service returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("AI service unreachable: {}", ex.getMessage());
            throw new AiServiceUnavailableException("AI service is unreachable", ex);
        } catch (Exception ex) {
            // Covers the timeout() operator's TimeoutException and anything else unexpected
            // (a malformed body Jackson can't even deserialize into RawAiPredictionResponse).
            log.warn("AI service call failed", ex);
            throw new AiServiceUnavailableException("AI service call failed", ex);
        }

        return validateAndMap(raw);
    }

    /**
     * Never trusts the external service's response shape as-is (Section 5.4,
     * step 4) — every field that becomes part of a persisted CropScan row or
     * a confidence-based decision is checked here first.
     */
    private AiPredictionResponse validateAndMap(RawAiPredictionResponse raw) {
        if (raw == null
                || isBlank(raw.cropName())
                || isBlank(raw.diseaseName())
                || isBlank(raw.classLabel())
                || isBlank(raw.modelVersion())
                || raw.confidenceScore() == null
                || raw.confidenceScore().compareTo(BigDecimal.ZERO) < 0
                || raw.confidenceScore().compareTo(BigDecimal.ONE) > 0) {
            log.warn("AI service returned a malformed prediction body: {}", raw);
            throw new AiServiceUnavailableException("AI service returned an invalid response");
        }

        return new AiPredictionResponse(
                raw.cropName(),
                raw.diseaseName(),
                raw.confidenceScore(),
                raw.severity(),
                raw.modelVersion(),
                raw.classLabel()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
