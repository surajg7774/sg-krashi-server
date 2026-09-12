package com.sgkrashi.booking.scheduler;

import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.booking.repository.BookingRepository;
import com.sgkrashi.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Closes the gap {@code ReviewEligibilityServiceImpl} and {@code
 * BookingRepository} used to work around: {@code BookingStatus.COMPLETED}
 * existed as an enum constant but nothing ever set it. Runs once daily,
 * transitioning every still-CONFIRMED booking whose stay/rental has already
 * ended to COMPLETED, via the same {@link BookingService#markCompleted}
 * method the admin manual-override path uses — so exactly one code path ever
 * fires {@code BookingCompletedEvent}/the customer notification, regardless
 * of which trigger caused the transition.
 *
 * <p>Idempotent by construction: {@link BookingRepository#findByStatusAndEndDateBefore}
 * only ever returns still-CONFIRMED rows, so a booking this job (or the admin
 * override) already completed simply won't be selected again — running this
 * job twice in a row, or re-running it after a partial failure, is always
 * safe.
 */
@Component
public class BookingCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(BookingCompletionJob.class);
    private static final ZoneId BOOKING_ZONE = ZoneId.of("Asia/Kolkata");

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public BookingCompletionJob(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    /** Runs daily at 1:00 AM IST — well clear of midnight day-boundary edge cases, and long before typical business hours. */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
    public void completeElapsedBookings() {
        runOnce();
    }

    /**
     * Split out from the {@code @Scheduled} method so this can be triggered
     * on demand (e.g. {@code AdminBookingController}'s manual-run endpoint,
     * used to verify this job without waiting for its real schedule) without
     * waiting for the real schedule — the actual transition logic doesn't
     * care why it's running.
     *
     * @return how many bookings were transitioned this run
     */
    public int runOnce() {
        LocalDate today = LocalDate.now(BOOKING_ZONE);
        List<Booking> elapsed = bookingRepository.findByStatusAndEndDateBefore(BookingStatus.CONFIRMED, today);

        if (elapsed.isEmpty()) {
            log.info("BookingCompletionJob ran at {}: no CONFIRMED bookings past their end date, nothing to do", today);
            return 0;
        }

        for (Booking booking : elapsed) {
            bookingService.markCompleted(booking.getId());
        }
        log.info("BookingCompletionJob ran at {}: transitioned {} booking(s) to COMPLETED (ids: {})",
                today, elapsed.size(), elapsed.stream().map(Booking::getId).toList());
        return elapsed.size();
    }
}
