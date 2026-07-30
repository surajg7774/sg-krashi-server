package com.sgkrashi.booking.dto.request;

import com.sgkrashi.booking.entity.BookableType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code startDate} is inclusive, {@code endDate} is exclusive — see
 * {@code Booking}'s Javadoc for the full convention. Both must be in the
 * future; same-day bookings aren't supported in this module.
 *
 * <p>{@code guestCount} (added in Module 9) is optional and only meaningful
 * for {@code STAY} bookings — ignored for {@code EQUIPMENT}.
 */
public record CreateBookingRequest(
        @NotNull(message = "Bookable type is required") BookableType bookableType,
        @NotNull(message = "Bookable item is required") Long bookableId,
        @NotNull(message = "Start date is required") @Future(message = "Start date must be in the future") LocalDate startDate,
        @NotNull(message = "End date is required") @Future(message = "End date must be in the future") LocalDate endDate,
        @Min(value = 1, message = "Guest count must be at least 1") Integer guestCount
) {
}
