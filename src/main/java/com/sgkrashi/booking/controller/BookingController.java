package com.sgkrashi.booking.controller;

import com.sgkrashi.booking.dto.request.CancelBookingRequest;
import com.sgkrashi.booking.dto.request.CreateBookingRequest;
import com.sgkrashi.booking.dto.response.AvailabilityResponse;
import com.sgkrashi.booking.dto.response.BookingResponse;
import com.sgkrashi.booking.dto.response.DateRangeResponse;
import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.service.AvailabilityService;
import com.sgkrashi.booking.service.BookingService;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final AvailabilityService availabilityService;

    public BookingController(BookingService bookingService, AvailabilityService availabilityService) {
        this.bookingService = bookingService;
        this.availabilityService = availabilityService;
    }

    /** Public, unauthenticated — advisory only, same as browsing a catalog; see AvailabilityService's Javadoc. */
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(
            @RequestParam BookableType bookableType,
            @RequestParam Long bookableId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        var result = availabilityService.checkAvailability(bookableType, bookableId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(result, "Availability checked"));
    }

    /** Public, unauthenticated — booked ranges in a window, for the calendar's disabled dates. */
    @GetMapping("/availability/calendar")
    public ResponseEntity<ApiResponse<List<DateRangeResponse>>> calendar(
            @RequestParam BookableType bookableType,
            @RequestParam Long bookableId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var result = availabilityService.getBookedRanges(bookableType, bookableId, from, to);
        return ResponseEntity.ok(ApiResponse.success(result, "Booked ranges retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.createBooking(request), "Booking created"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PaginatedResponse<BookingResponse>>> listMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.listMyBookings(page, size), "Bookings retrieved"));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<BookingResponse>> getOne(@PathVariable Long bookingId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingDetail(bookingId), "Booking retrieved"));
    }

    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @PathVariable Long bookingId,
            @RequestBody(required = false) CancelBookingRequest request
    ) {
        CancelBookingRequest effectiveRequest = request != null ? request : new CancelBookingRequest(null);
        return ResponseEntity.ok(ApiResponse.success(bookingService.cancelBooking(bookingId, effectiveRequest), "Booking cancelled"));
    }
}
