package com.sgkrashi.analytics.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code historical} reuses {@link RevenueReportResponse.RevenuePoint}
 * exactly, so the frontend can plot both series with the same shape;
 * {@code forecast} is a separate, visually distinct series (see
 * {@code RevenueForecastChart.tsx}'s dashed projected line).
 */
public record RevenueForecastResponse(
        List<RevenueReportResponse.RevenuePoint> historical,
        List<ForecastPoint> forecast,
        String technique
) {

    public record ForecastPoint(String bucket, BigDecimal projectedRevenue) {
    }
}
