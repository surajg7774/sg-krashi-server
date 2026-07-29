package com.sgkrashi.cart.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String productSlug,
        String thumbnailUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int availableStock
) {
}
