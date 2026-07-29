package com.sgkrashi.cropmarketplace.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CropListingFilterRequest(
        String cropType,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LocalDate harvestDateFrom,
        LocalDate harvestDateTo,
        String search,
        int page,
        int size
) {
}
