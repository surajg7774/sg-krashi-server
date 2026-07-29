package com.sgkrashi.productstore.dto.request;

import java.math.BigDecimal;

public record ProductFilterRequest(
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean organicOnly,
        String search,
        int page,
        int size
) {
}
