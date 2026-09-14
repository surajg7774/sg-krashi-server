package com.sgkrashi.mandi.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MandiPriceResponse(
        Long id,
        String commodity,
        String marketName,
        String state,
        String district,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal modalPrice,
        LocalDate priceDate
) {
}
