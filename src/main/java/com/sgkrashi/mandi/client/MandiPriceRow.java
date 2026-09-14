package com.sgkrashi.mandi.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One parsed row from the government API, before it's upserted into {@code MandiPrice}. */
public record MandiPriceRow(
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
