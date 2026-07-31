package com.sgkrashi.booking.dto.response;

import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AdminBookingResponse(
        Long id,
        BookableType bookableType,
        Long bookableId,
        String bookableName,
        String thumbnailUrl,
        Long userId,
        String userName,
        String userEmail,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        BigDecimal totalPrice,
        Instant cancelledAt,
        String cancellationReason,
        String adminNotes,
        boolean refunded,
        Instant refundedAt,
        Instant createdAt,
        Integer guestCount
) {
}
