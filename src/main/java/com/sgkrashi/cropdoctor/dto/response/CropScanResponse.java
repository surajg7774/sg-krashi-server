package com.sgkrashi.cropdoctor.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CropScanResponse(
        Long id,
        String declaredCrop,
        String imageUrl,
        String cropName,
        String diseaseName,
        BigDecimal confidenceScore,
        String severity,
        String recommendation,
        String modelVersion,
        boolean isUncertain,
        boolean cropMismatch,
        Instant createdAt
) {
}
