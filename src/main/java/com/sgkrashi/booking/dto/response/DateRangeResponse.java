package com.sgkrashi.booking.dto.response;

import java.time.LocalDate;

/** {@code startDate} inclusive, {@code endDate} exclusive — same convention as {@code Booking}. */
public record DateRangeResponse(LocalDate startDate, LocalDate endDate) {
}
