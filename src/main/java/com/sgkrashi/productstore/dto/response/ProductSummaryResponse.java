package com.sgkrashi.productstore.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        boolean isOrganicCertified,
        int stockQty,
        String categoryName,
        String thumbnailUrl,
        BigDecimal avgRating,
        int reviewCount,
        boolean isActive,
        Instant createdAt
) {
}
