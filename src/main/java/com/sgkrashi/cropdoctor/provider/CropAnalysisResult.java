package com.sgkrashi.cropdoctor.provider;

import java.util.List;

/**
 * Provider-agnostic analysis result — {@code CropDoctorService} depends only
 * on this shape, never on anything Gemini- or Plantix-specific. This is the
 * seam a future {@code PlantixAnalysisProvider} implementation attaches to.
 */
public record CropAnalysisResult(
        String identifiedCrop,
        boolean cropMatchesDeclared,
        HealthStatus healthStatus,
        String problem,
        String pathogenScientificName,
        ConfidenceBand confidenceBand,
        Severity severity,
        List<String> symptoms,
        List<String> possibleCauses,
        List<String> environmentalFactors,
        List<String> actionsNow,
        List<String> prevention,
        String monitoringGuidance,
        List<String> warningSignsToEscalate,
        String limitations,
        String providerName,
        String providerModelVersion,
        // Empty when no knowledge-base entries were retrieved/used for this
        // scan (e.g. RetrievalService found nothing for the declared crop,
        // or the active provider doesn't do retrieval at all) — never
        // populated with anything that wasn't genuinely included in the
        // prompt actually sent.
        List<GroundingSource> groundingSources
) {
}
