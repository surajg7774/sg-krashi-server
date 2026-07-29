package com.sgkrashi.booking.service;

import com.sgkrashi.booking.dto.request.CancelBookingRequest;
import com.sgkrashi.booking.dto.request.CreateBookingRequest;
import com.sgkrashi.booking.dto.response.BookingResponse;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.common.dto.PaginatedResponse;

public interface BookingService {

    /**
     * Creates a booking for the authenticated user, rejecting it if the
     * requested range overlaps any existing PENDING_PAYMENT/CONFIRMED booking
     * for the same bookable item. See {@code BookingServiceImpl} for the full
     * correctness writeup.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the bookable item doesn't exist
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the date range is invalid (end before/equal to start)
     * @throws com.sgkrashi.common.exception.ConflictException if the range overlaps an existing booking
     */
    BookingResponse createBooking(CreateBookingRequest request);

    PaginatedResponse<BookingResponse> listMyBookings(int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if the booking doesn't exist or isn't the caller's */
    BookingResponse getBookingDetail(Long bookingId);

    /**
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the booking doesn't exist or isn't the caller's
     * @throws com.sgkrashi.common.exception.BusinessRuleException if outside the free-cancellation window or already terminal
     */
    BookingResponse cancelBooking(Long bookingId, CancelBookingRequest request);

    /** Internal lookup used by the payment module — not exposed over HTTP. */
    Booking getBookingEntityOrThrow(Long bookingId);

    /** Transitions a booking to CONFIRMED once its payment has settled. Idempotent no-op if already CONFIRMED. */
    void markConfirmed(Long bookingId);

    /**
     * A failed payment CANCELS the booking outright (see {@code BookingStatus}'s
     * Javadoc) rather than leaving it pending — this immediately frees the
     * calendar slot for other customers.
     */
    void markPaymentFailed(Long bookingId);
}
