package com.sgkrashi.analytics.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record RevenueReportResponse(List<RevenuePoint> points, BigDecimal totalRevenue) {

    /**
     * {@code bucket} is a date-bucket label whose granularity matches the
     * request's {@code groupBy} (e.g. {@code "2026-07-15"} for day,
     * {@code "2026-07"} for month) — computed in SQL via {@code DATE_FORMAT},
     * never reformatted from a fetched {@code Instant} in Java.
     */
    public record RevenuePoint(String bucket, BigDecimal orderRevenue, BigDecimal bookingRevenue, BigDecimal total) {
    }
}
