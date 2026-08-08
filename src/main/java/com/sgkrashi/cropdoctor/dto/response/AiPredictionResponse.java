package com.sgkrashi.cropdoctor.dto.response;

import java.math.BigDecimal;

/**
 * Shape of a successful response from the Python AI service's {@code POST
 * /predict} — deserialized by {@code AiServiceClient} and validated before
 * any of its fields are trusted (an external service's response is never
 * assumed well-formed).
 */
public record AiPredictionResponse(
        String cropName,
        String diseaseName,
        BigDecimal confidenceScore,
        String severity,
        String modelVersion,
        String classLabel
) {
}
