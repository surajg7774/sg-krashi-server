package com.sgkrashi.analytics.dto.response;

import java.util.List;

public record StockRiskResponse(List<StockRiskItem> items) {

    /** {@code daysRemaining} is null when there's no recent sales velocity at all — nothing is currently depleting the stock, so "days until stock-out" isn't a meaningful number. */
    public record StockRiskItem(
            Long productId,
            String productName,
            int currentStock,
            double avgDailySales,
            Double daysRemaining,
            boolean isAtRisk
    ) {
    }
}
