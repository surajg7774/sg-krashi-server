package com.sgkrashi.farmstay.dto.response;

import java.math.BigDecimal;

public record StayListingSummaryResponse(
        Long id,
        String name,
        String slug,
        String city,
        String state,
        int maxGuests,
        BigDecimal nightlyRate,
        boolean isAvailable,
        String thumbnailUrl
) {
}
