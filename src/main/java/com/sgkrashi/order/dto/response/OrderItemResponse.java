package com.sgkrashi.order.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        String thumbnailUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {}
