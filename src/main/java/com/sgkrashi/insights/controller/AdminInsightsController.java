package com.sgkrashi.insights.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.ActivityResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.BookingsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.FulfilmentResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.OrdersResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SignupsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SnapshotResponse;
import com.sgkrashi.insights.service.AdminInsightsService;
import com.sgkrashi.insights.util.Granularity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin Insights: signups, orders, fulfilment times, bookings, activity and a few current-state counters, all
 * read-only GETs over existing tables. {@code from}/{@code to} are India-time calendar dates, {@code to} inclusive;
 * {@code groupBy} is day, week (Monday start) or month.
 */
@RestController
@RequestMapping("/api/v1/admin/insights")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminInsightsController {

    private final AdminInsightsService service;

    public AdminInsightsController(AdminInsightsService service) {
        this.service = service;
    }

    @GetMapping("/signups")
    public ResponseEntity<ApiResponse<SignupsResponse>> signups(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy) {
        return ResponseEntity.ok(ApiResponse.success(service.signups(from, to, Granularity.parse(groupBy)), "Signup insights retrieved"));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<OrdersResponse>> orders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy) {
        return ResponseEntity.ok(ApiResponse.success(service.orders(from, to, Granularity.parse(groupBy)), "Order insights retrieved"));
    }

    @GetMapping("/fulfilment")
    public ResponseEntity<ApiResponse<FulfilmentResponse>> fulfilment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(service.fulfilment(from, to), "Fulfilment insights retrieved"));
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<BookingsResponse>> bookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String groupBy) {
        return ResponseEntity.ok(ApiResponse.success(service.bookings(from, to, Granularity.parse(groupBy)), "Booking insights retrieved"));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<ActivityResponse>> activity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "week") String groupBy) {
        return ResponseEntity.ok(ApiResponse.success(service.activity(from, to, Granularity.parse(groupBy)), "Activity insights retrieved"));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<SnapshotResponse>> snapshot() {
        return ResponseEntity.ok(ApiResponse.success(service.snapshot(), "Insights snapshot retrieved"));
    }
}
