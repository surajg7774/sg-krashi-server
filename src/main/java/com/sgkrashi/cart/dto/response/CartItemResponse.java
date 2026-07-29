package com.sgkrashi.cart.dto.response;

import com.sgkrashi.common.entity.ItemType;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        ItemType itemType,
        Long itemId,
        String itemName,
        String itemSlug,
        String thumbnailUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int availableQuantity
) {
}
