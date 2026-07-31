package com.sgkrashi.booking.service;

import com.sgkrashi.booking.dto.request.CancelBookingRequest;
import com.sgkrashi.booking.dto.request.CreateBookingRequest;
import com.sgkrashi.booking.dto.response.AdminBookingResponse;
import com.sgkrashi.booking.dto.response.BookingResponse;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.common.dto.PaginatedResponse;

import java.time.Instant;

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

    /**
     * Transitions a booking to CANCELLED once {@code RefundService} has
     * already processed the real gateway refund — reuses {@code CANCELLED}
     * rather than a distinct REFUNDED state (unlike Order, which had no
     * CANCELLED state to reuse) since a refunded booking IS, semantically, a
     * cancelled one; the {@code Payment}'s own REFUNDED status is what
     * distinguishes it from a plain admin cancellation. Idempotent: if already
     * CANCELLED (e.g. an earlier plain cancel with no refund), the status and
     * {@code cancelledAt}/{@code cancellationReason} are left untouched — only
     * the Payment side changes in that case.
     */
    void markRefunded(Long bookingId);

    /** Admin-wide, unfiltered-by-owner listing (Module 16) — every booking platform-wide. */
    PaginatedResponse<AdminBookingResponse> listBookingsForAdmin(
            BookingStatus status, Long userId, Instant dateFrom, Instant dateTo, int page, int size);

    /** Admin detail view — unlike {@link #getBookingDetail}, not scoped to the caller's own bookings. */
    AdminBookingResponse getBookingDetailForAdmin(Long bookingId);

    /**
     * Admin-driven status change, bypassing the customer-facing cancellation
     * window entirely (an admin override, not a self-service cancel — see
     * {@code cancelBooking} for that path). {@code adminNotes} is always
     * applied, independent of whether the status actually changes.
     *
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the booking is already CANCELLED/COMPLETED and a different status is requested
     */
    AdminBookingResponse updateBookingStatus(Long bookingId, BookingStatus newStatus, String adminNotes);
}
