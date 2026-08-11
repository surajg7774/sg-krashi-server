package com.sgkrashi.cropdoctor.dto.response;

import java.time.Instant;

public record CropScanSummaryResponse(
        Long id,
        String imageUrl,
        String identifiedCrop,
        String problem,
        String healthStatus,
        String confidenceBand,
        boolean isUncertain,
        boolean cropMismatch,
        Instant createdAt
) {
}
