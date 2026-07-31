package com.sgkrashi.booking.controller;

import com.sgkrashi.booking.dto.request.AdminBookingStatusUpdateRequest;
import com.sgkrashi.booking.dto.response.AdminBookingResponse;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.booking.service.BookingService;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.payment.dto.response.RefundResultResponse;
import com.sgkrashi.payment.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Platform-wide Booking visibility and management (Module 16) — every Admin/Super Admin sees every booking. */
@RestController
@RequestMapping("/api/v1/admin/bookings")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminBookingController {

    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");

    private final BookingService bookingService;
    private final RefundService refundService;

    public AdminBookingController(BookingService bookingService, RefundService refundService) {
        this.bookingService = bookingService;
        this.refundService = refundService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminBookingResponse>>> list(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Instant from = dateFrom == null ? null : dateFrom.atStartOfDay(ADMIN_ZONE).toInstant();
        Instant to = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ADMIN_ZONE).toInstant();
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.listBookingsForAdmin(status, userId, from, to, page, size), "Bookings retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminBookingResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingDetailForAdmin(id), "Booking retrieved"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminBookingResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminBookingStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.updateBookingStatus(id, request.status(), request.adminNotes()), "Booking status updated"));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<RefundResultResponse>> refund(@PathVariable Long id) {
        RefundResultResponse result = refundService.refundBooking(id);
        String message = result.alreadyRefunded() ? "This booking was already refunded" : "Refund processed";
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }
}
