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
        String providerModelVersion
) {
}
