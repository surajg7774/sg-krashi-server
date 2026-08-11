package com.sgkrashi.cropdoctor.provider.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The structured report body Gemini returns inside its response text,
 * shaped exactly by {@link GeminiResponseSchema}. Kept separate from the
 * provider-agnostic {@code CropAnalysisResult} — this type is allowed to
 * know it's talking to Gemini; {@code CropAnalysisResult} is not.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiReportJson(
        String identifiedCrop,
        Boolean cropMatchesDeclared,
        String healthStatus,
        String problem,
        String pathogenScientificName,
        String confidenceBand,
        String severity,
        List<String> symptoms,
        List<String> possibleCauses,
        List<String> environmentalFactors,
        List<String> actionsNow,
        List<String> prevention,
        String monitoringGuidance,
        List<String> warningSignsToEscalate,
        String limitations
) {
}
