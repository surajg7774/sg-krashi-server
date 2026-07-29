package com.sgkrashi.booking.service.impl;

import com.sgkrashi.booking.dto.response.AvailabilityResponse;
import com.sgkrashi.booking.dto.response.DateRangeResponse;
import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.booking.repository.BookingRepository;
import com.sgkrashi.booking.service.AvailabilityService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;

    public AvailabilityServiceImpl(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public AvailabilityResponse checkAvailability(BookableType bookableType, Long bookableId, LocalDate startDate, LocalDate endDate) {
        List<Booking> overlapping = bookingRepository.findOverlapping(
                bookableType, bookableId, BLOCKING_STATUSES, startDate, endDate);

        List<DateRangeResponse> conflicts = overlapping.stream()
                .map(booking -> new DateRangeResponse(booking.getStartDate(), booking.getEndDate()))
                .toList();

        return new AvailabilityResponse(conflicts.isEmpty(), conflicts);
    }

    @Override
    public List<DateRangeResponse> getBookedRanges(BookableType bookableType, Long bookableId, LocalDate from, LocalDate to) {
        return bookingRepository.findOverlapping(bookableType, bookableId, BLOCKING_STATUSES, from, to).stream()
                .map(booking -> new DateRangeResponse(booking.getStartDate(), booking.getEndDate()))
                .toList();
    }
}
