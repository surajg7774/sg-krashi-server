package com.sgkrashi.cropdoctor.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Raw JSON shape returned by the Python AI service's {@code POST /predict}
 * (snake_case, per its Pydantic response model) — deserialized here and
 * validated in {@code AiServiceClientImpl} before being converted to the
 * trusted {@link AiPredictionResponse}. Never passed further down as-is: an
 * external service's response is never assumed well-formed.
 */
public record RawAiPredictionResponse(
        @JsonProperty("crop_name") String cropName,
        @JsonProperty("disease_name") String diseaseName,
        @JsonProperty("confidence_score") BigDecimal confidenceScore,
        @JsonProperty("severity") String severity,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("class_label") String classLabel
) {
}
