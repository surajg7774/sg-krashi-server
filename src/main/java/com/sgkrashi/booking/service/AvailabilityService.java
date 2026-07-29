package com.sgkrashi.booking.service;

import com.sgkrashi.booking.dto.response.AvailabilityResponse;
import com.sgkrashi.booking.dto.response.DateRangeResponse;
import com.sgkrashi.booking.entity.BookableType;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only advisory checks — no locking. The real guarantee against
 * double-booking comes from {@code BookingServiceImpl.createBooking}'s
 * transactional, locked check; a race between "this endpoint said available"
 * and "booking creation" is expected (the calendar can go stale between page
 * load and click) and is handled correctly there, not here.
 */
public interface AvailabilityService {

    AvailabilityResponse checkAvailability(BookableType bookableType, Long bookableId, LocalDate startDate, LocalDate endDate);

    /** Booked (blocking-status) ranges for a bookable item within a window, for the frontend calendar. */
    List<DateRangeResponse> getBookedRanges(BookableType bookableType, Long bookableId, LocalDate from, LocalDate to);
}
