package com.sgkrashi.productstore.dto.response;

import java.math.BigDecimal;

public record ProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        boolean isOrganicCertified,
        int stockQty,
        String categoryName,
        String thumbnailUrl
) {
}
