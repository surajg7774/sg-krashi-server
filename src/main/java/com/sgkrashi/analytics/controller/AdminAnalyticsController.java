package com.sgkrashi.analytics.controller;

import com.sgkrashi.analytics.dto.response.ConversionReportResponse;
import com.sgkrashi.analytics.dto.response.OccupancyReportResponse;
import com.sgkrashi.analytics.dto.response.RevenueReportResponse;
import com.sgkrashi.analytics.dto.response.TopListingsResponse;
import com.sgkrashi.analytics.service.AnalyticsService;
import com.sgkrashi.common.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Read-only aggregation reports, extending Module 14's dashboard — every
 * endpoint here is a plain GET against existing tables, no mutation, same
 * risk profile as Module 18. {@code from}/{@code to} are calendar dates on
 * the URL (matching Module 16's admin date-filter convention), anchored to
 * IST for the Instant-based reports the same way {@code
 * AdminDashboardServiceImpl} already anchors "today".
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminAnalyticsController {

    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<RevenueReportResponse>> revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy
    ) {
        var result = analyticsService.getRevenueReport(toInstant(from), toExclusiveEndInstant(to), groupBy);
        return ResponseEntity.ok(ApiResponse.success(result, "Revenue report retrieved"));
    }

    @GetMapping("/top-listings")
    public ResponseEntity<ApiResponse<TopListingsResponse>> topListings(
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit
    ) {
        var result = analyticsService.getTopListings(type, toInstant(from), toExclusiveEndInstant(to), limit);
        return ResponseEntity.ok(ApiResponse.success(result, "Top listings retrieved"));
    }

    @GetMapping("/occupancy")
    public ResponseEntity<ApiResponse<OccupancyReportResponse>> occupancy(
            @RequestParam String bookableType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        // 'to' is inclusive on the URL, same as every other report here, but
        // the occupancy query's window is exclusive-end (matching Booking's
        // own convention) — bump by a day so the picked end date is fully
        // included, e.g. from=07-01/to=07-31 covers all 31 days of July.
        var result = analyticsService.getOccupancyReport(bookableType, from, to.plusDays(1));
        return ResponseEntity.ok(ApiResponse.success(result, "Occupancy report retrieved"));
    }

    @GetMapping("/conversion")
    public ResponseEntity<ApiResponse<ConversionReportResponse>> conversion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var result = analyticsService.getConversionReport(toInstant(from), toExclusiveEndInstant(to));
        return ResponseEntity.ok(ApiResponse.success(result, "Conversion report retrieved"));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String report,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bookableType,
            @RequestParam(defaultValue = "10") int limit
    ) {
        String csv = analyticsService.exportCsv(report, toInstant(from), toExclusiveEndInstant(to), groupBy, type, bookableType, limit);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        String filename = report + "-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    private Instant toInstant(LocalDate date) {
        return date.atStartOfDay(ADMIN_ZONE).toInstant();
    }

    /** {@code to} is inclusive on the URL (a calendar date the admin picked) but every query below is exclusive-end — this pushes it to the start of the NEXT day so that whole day is included. */
    private Instant toExclusiveEndInstant(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ADMIN_ZONE).toInstant();
    }
}
