package com.sgkrashi.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A lock anchor row, one per distinct {@code (bookableType, bookableId)} —
 * NOT the bookable resource itself. See {@code BookingServiceImpl}'s Javadoc
 * for why the booking engine locks this instead of the underlying
 * Equipment/StayListing row: locking the resource's own row directly would
 * require this generic engine to depend on module-specific entities
 * (Equipment today, StayListing from Module 9), defeating the point of a
 * shared, bookable-type-agnostic engine. This table has no other purpose —
 * it carries no data beyond the identity it locks.
 */
@Entity
@Table(name = "booking_locks")
public class BookingLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "bookable_type", nullable = false, length = 20)
    private BookableType bookableType;

    @Column(name = "bookable_id", nullable = false)
    private Long bookableId;

    public Long getId() {
        return id;
    }

    public BookableType getBookableType() {
        return bookableType;
    }

    public void setBookableType(BookableType bookableType) {
        this.bookableType = bookableType;
    }

    public Long getBookableId() {
        return bookableId;
    }

    public void setBookableId(Long bookableId) {
        this.bookableId = bookableId;
    }
}
