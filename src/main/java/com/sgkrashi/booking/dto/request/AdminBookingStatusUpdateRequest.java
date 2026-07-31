package com.sgkrashi.booking.dto.request;

import com.sgkrashi.booking.entity.BookingStatus;
import jakarta.validation.constraints.NotNull;

/** {@code adminNotes} is always applied, independent of whether {@code status} actually changes — see {@code BookingService#updateBookingStatus}'s Javadoc. */
public record AdminBookingStatusUpdateRequest(
        @NotNull(message = "Status is required")
        BookingStatus status,

        String adminNotes
) {
}
