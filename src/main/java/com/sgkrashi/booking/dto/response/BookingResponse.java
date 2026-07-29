package com.sgkrashi.booking.dto.response;

import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        BookableType bookableType,
        Long bookableId,
        String bookableName,
        String thumbnailUrl,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        BigDecimal totalPrice,
        boolean cancellable,
        Instant cancelledAt,
        String cancellationReason,
        Instant createdAt
) {
}
