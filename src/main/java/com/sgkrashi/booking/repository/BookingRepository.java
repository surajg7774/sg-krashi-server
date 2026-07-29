package com.sgkrashi.booking.repository;

import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

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
}
