package com.sgkrashi.order.dto.response;

import com.sgkrashi.common.entity.ItemType;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        ItemType itemType,
        Long itemId,
        String itemName,
        String thumbnailUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {}
