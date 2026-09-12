package com.sgkrashi.booking.repository;

import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * All bookings for a bookable item in a blocking status, overlapping
     * {@code [rangeStart, rangeEnd)} — see {@code Booking}'s Javadoc for the
     * inclusive-start/exclusive-end convention this comparison relies on.
     * Plain (non-locking) read, for the read-only availability calendar only
     * — see {@link #findOverlappingForUpdate} for the version that MUST be
     * used at actual booking-creation time, and why.
     */
    @Query("""
            select b from Booking b
            where b.bookableType = :bookableType
              and b.bookableId = :bookableId
              and b.status in :blockingStatuses
              and b.startDate < :rangeEnd
              and b.endDate > :rangeStart
            order by b.startDate asc
            """)
    List<Booking> findOverlapping(
            @Param("bookableType") BookableType bookableType,
            @Param("bookableId") Long bookableId,
            @Param("blockingStatuses") List<BookingStatus> blockingStatuses,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );

    /**
     * Same query as {@link #findOverlapping}, but as a locking read — REQUIRED
     * at booking-creation time, after acquiring the {@code BookingLock} for
     * this {@code (bookableType, bookableId)} pair.
     *
     * <p><b>Why a lock here too, when {@code BookingLock} already serializes
     * concurrent attempts:</b> serializing WHEN two transactions run doesn't
     * by itself guarantee WHAT they see. Under MySQL's default REPEATABLE READ,
     * a transaction's plain (non-locking) reads are answered from the
     * consistent snapshot established at that transaction's first read — a
     * snapshot taken before this request even reached this method (e.g. at
     * its own auth lookup). A second transaction that waits on the
     * {@code BookingLock} and then runs a PLAIN select here would still only
     * see ITS OWN old snapshot, from before the first transaction committed —
     * so it would miss the first transaction's just-inserted booking entirely,
     * despite executing after it in wall-clock time. This was caught directly
     * by a concurrency test: two identical-range booking requests both
     * succeeded, and SQL-level timing logs showed the second request's
     * {@code BookingLock} acquisition genuinely blocked (~75ms) on the first,
     * proving the lock worked — but its subsequent plain overlap SELECT still
     * missed the first transaction's already-committed row. A LOCKING read
     * (any lock mode) is specifically exempted from this snapshot rule by
     * InnoDB — it always reads the latest committed version of a row — which
     * is the exact property Module 6/7 relied on for stock/quantity checks,
     * and is why this method, not {@link #findOverlapping}, must be used here.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from Booking b
            where b.bookableType = :bookableType
              and b.bookableId = :bookableId
              and b.status in :blockingStatuses
              and b.startDate < :rangeEnd
              and b.endDate > :rangeStart
            order by b.startDate asc
            """)
    List<Booking> findOverlappingForUpdate(
            @Param("bookableType") BookableType bookableType,
            @Param("bookableId") Long bookableId,
            @Param("blockingStatuses") List<BookingStatus> blockingStatuses,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );

    /**
     * Candidate bookings for Module 12's review eligibility check. {@code
     * COMPLETED} is the real, primary signal — set by {@code
     * BookingCompletionJob} once {@code endDate} has passed — with the
     * {@code CONFIRMED and endDate < today} half kept only as a same-day
     * fallback; see {@link #findEligibleBookingById}'s Javadoc for why.
     */
    @Query("""
            select b from Booking b
            where b.userId = :userId
              and b.bookableType = :bookableType
              and b.bookableId = :bookableId
              and (b.status = com.sgkrashi.booking.entity.BookingStatus.COMPLETED
                   or (b.status = com.sgkrashi.booking.entity.BookingStatus.CONFIRMED and b.endDate < :today))
            order by b.endDate desc
            """)
    List<Booking> findEligibleForReview(
            @Param("userId") Long userId,
            @Param("bookableType") BookableType bookableType,
            @Param("bookableId") Long bookableId,
            @Param("today") LocalDate today
    );

    /**
     * The {@code CONFIRMED and endDate < today} half of this OR is a
     * deliberately-kept fallback, not a leftover: {@code BookingCompletionJob}
     * runs once daily, so a booking whose {@code endDate} passed only hours
     * ago may not have been flipped to COMPLETED yet by the time a customer
     * tries to review it same-day. Without this fallback that customer would
     * see "not eligible" for up to ~24h for no real reason. COMPLETED is the
     * primary, real signal; this proxy only closes that same-day gap.
     */
    @Query("""
            select b from Booking b
            where b.id = :bookingId
              and b.userId = :userId
              and b.bookableType = :bookableType
              and b.bookableId = :bookableId
              and (b.status = com.sgkrashi.booking.entity.BookingStatus.COMPLETED
                   or (b.status = com.sgkrashi.booking.entity.BookingStatus.CONFIRMED and b.endDate < :today))
            """)
    Optional<Booking> findEligibleBookingById(
            @Param("bookingId") Long bookingId,
            @Param("userId") Long userId,
            @Param("bookableType") BookableType bookableType,
            @Param("bookableId") Long bookableId,
            @Param("today") LocalDate today
    );

    long countByUserId(Long userId);

    long countByStatus(BookingStatus status);

    long countByStatusAndStartDateGreaterThanEqual(BookingStatus status, LocalDate startDate);

    /**
     * {@code BookingCompletionJob}'s daily query — every still-CONFIRMED
     * booking whose stay/rental has already ended, regardless of when it
     * ended. Naturally idempotent: once a row is transitioned to COMPLETED it
     * no longer matches {@code status = CONFIRMED}, so running the job twice
     * (or against a booking a previous run already handled) finds nothing to
     * redo.
     */
    List<Booking> findByStatusAndEndDateBefore(BookingStatus status, LocalDate endDate);
}
