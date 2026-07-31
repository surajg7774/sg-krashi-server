package com.sgkrashi.booking.mapper;

import com.sgkrashi.booking.dto.response.AdminBookingResponse;
import com.sgkrashi.booking.dto.response.BookingResponse;
import com.sgkrashi.booking.entity.Booking;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking, String bookableName, String thumbnailUrl, boolean cancellable) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookableType(),
                booking.getBookableId(),
                bookableName,
                thumbnailUrl,
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getStatus(),
                booking.getTotalPrice(),
                cancellable,
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                booking.getCreatedAt(),
                booking.getGuestCount()
        );
    }

    public AdminBookingResponse toAdminResponse(
            Booking booking, String bookableName, String thumbnailUrl,
            String userName, String userEmail, boolean refunded, Instant refundedAt
    ) {
        return new AdminBookingResponse(
                booking.getId(),
                booking.getBookableType(),
                booking.getBookableId(),
                bookableName,
                thumbnailUrl,
                booking.getUserId(),
                userName,
                userEmail,
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getStatus(),
                booking.getTotalPrice(),
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                booking.getAdminNotes(),
                refunded,
                refundedAt,
                booking.getCreatedAt(),
                booking.getGuestCount()
        );
    }
}
