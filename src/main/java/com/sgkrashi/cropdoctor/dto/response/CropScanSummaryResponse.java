package com.sgkrashi.cropdoctor.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CropScanSummaryResponse(
        Long id,
        String imageUrl,
        String cropName,
        String diseaseName,
        BigDecimal confidenceScore,
        boolean isUncertain,
        boolean cropMismatch,
        Instant createdAt
) {
}
