package com.sgkrashi.equipmentrental.dto.response;

import java.math.BigDecimal;

public record EquipmentSummaryResponse(
        Long id,
        String name,
        String slug,
        String category,
        BigDecimal dailyRate,
        boolean isAvailable,
        String thumbnailUrl,
        BigDecimal avgRating,
        int reviewCount
) {
}
