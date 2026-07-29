package com.sgkrashi.booking.dto.request;

import com.sgkrashi.booking.entity.BookableType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code startDate} is inclusive, {@code endDate} is exclusive — see
 * {@code Booking}'s Javadoc for the full convention. Both must be in the
 * future; same-day bookings aren't supported in this module.
 */
public record CreateBookingRequest(
        @NotNull(message = "Bookable type is required") BookableType bookableType,
        @NotNull(message = "Bookable item is required") Long bookableId,
        @NotNull(message = "Start date is required") @Future(message = "Start date must be in the future") LocalDate startDate,
        @NotNull(message = "End date is required") @Future(message = "End date must be in the future") LocalDate endDate
) {
}
