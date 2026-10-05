package com.sgkrashi.farmstay.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record StayListingSummaryResponse(
        Long id,
        String name,
        String slug,
        String city,
        String state,
        int maxGuests,
        BigDecimal nightlyRate,
        boolean isAvailable,
        String thumbnailUrl,
        BigDecimal avgRating,
        int reviewCount,
        boolean isActive,
        Instant createdAt
) {
}
