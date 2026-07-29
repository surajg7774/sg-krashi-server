package com.sgkrashi.booking.repository;

import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.BookingLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingLockRepository extends JpaRepository<BookingLock, Long> {

    Optional<BookingLock> findByBookableTypeAndBookableId(BookableType bookableType, Long bookableId);

    /**
     * Locks the anchor row for this {@code (bookableType, bookableId)} for the
     * duration of the caller's transaction — see {@code BookingLock}'s Javadoc
     * for why this row, not the underlying resource, is what gets locked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from BookingLock l where l.bookableType = :bookableType and l.bookableId = :bookableId")
    Optional<BookingLock> findForUpdate(
            @Param("bookableType") BookableType bookableType,
            @Param("bookableId") Long bookableId
    );
}
