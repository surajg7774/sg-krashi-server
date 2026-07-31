package com.sgkrashi.analytics.service;

import com.sgkrashi.analytics.dto.response.ConversionReportResponse;
import com.sgkrashi.analytics.dto.response.OccupancyReportResponse;
import com.sgkrashi.analytics.dto.response.RevenueReportResponse;
import com.sgkrashi.analytics.dto.response.TopListingsResponse;

import java.time.Instant;
import java.time.LocalDate;

public interface AnalyticsService {

    /** {@code groupBy} is one of {@code day}/{@code week}/{@code month}. */
    RevenueReportResponse getRevenueReport(Instant from, Instant to, String groupBy);

    /** {@code type} is one of {@code PRODUCT}/{@code CROP_LISTING}/{@code EQUIPMENT}/{@code STAY}. */
    TopListingsResponse getTopListings(String type, Instant from, Instant to, int limit);

    /** {@code bookableType} is one of {@code EQUIPMENT}/{@code STAY}. Every ACTIVE listing of that type is included, even ones with zero bookings (0% occupancy) — see impl. */
    OccupancyReportResponse getOccupancyReport(String bookableType, LocalDate from, LocalDate to);

    ConversionReportResponse getConversionReport(Instant from, Instant to);

    /**
     * Builds a CSV for one of the four report types, reusing the exact same
     * service methods above rather than a parallel export-specific query —
     * only the output formatting differs.
     */
    String exportCsv(String report, Instant from, Instant to, String groupBy, String type, String bookableType, int limit);
}
