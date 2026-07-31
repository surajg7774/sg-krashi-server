package com.sgkrashi.analytics.dto.response;

import java.util.List;

public record OccupancyReportResponse(List<OccupancyItem> items, int rangeDays) {

    /** {@code occupancyRate} is {@code bookedDays / rangeDays}, 0.0-1.0 — not a percentage string, the frontend formats that. */
    public record OccupancyItem(Long id, String name, int bookedDays, double occupancyRate) {
    }
}
