package com.sgkrashi.cropmarketplace.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CropListingSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal unitPrice,
        int quantityAvailable,
        LocalDate harvestDate,
        String categoryName,
        String thumbnailUrl
) {
}
