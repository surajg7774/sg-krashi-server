package com.sgkrashi.cropmarketplace.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CropListingSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal unitPrice,
        boolean isOrganicCertified,
        int quantityAvailable,
        LocalDate harvestDate,
        String categoryName,
        String thumbnailUrl,
        BigDecimal avgRating,
        int reviewCount,
        boolean isActive
) {
}
