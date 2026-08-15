package com.sgkrashi.cropdoctor.dto.response;

import java.time.Instant;
import java.util.List;

public record CropScanResponse(
        Long id,
        String declaredCrop,
        String language,
        List<String> imageUrls,
        String identifiedCrop,
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
        String limitations,
        String providerName,
        String modelVersion,
        boolean isUncertain,
        boolean cropMismatch,
        List<GroundingSourceResponse> groundingSources,
        Instant createdAt
) {
}
