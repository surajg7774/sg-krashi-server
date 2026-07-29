package com.sgkrashi.booking.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A generic date-range reservation against some other bookable resource,
 * identified polymorphic-lite via {@link #bookableType}/{@link #bookableId}
 * (same tradeoff as {@code MediaAsset.ownerType}/{@code ownerId} and
 * {@code Payment.payableType}/{@code payableId} — no FK, since it can point
 * at Equipment today and StayListing from Module 9 onward).
 *
 * <p><b>Date-range convention (read before touching any date-comparison logic
 * here or in {@code BookingServiceImpl}):</b> {@link #startDate} is inclusive,
 * {@link #endDate} is EXCLUSIVE — the same convention as hotel/car-rental
 * check-in/check-out dates. A booking from Monday to Thursday is 3 nights/days
 * (Mon, Tue, Wed occupied; Thursday is the return/checkout day, and the
 * equipment is free for a new booking starting that same Thursday). This is
 * why overlap detection uses strict {@code <} rather than {@code <=} — see
 * {@code BookingServiceImpl.rangesOverlap}.
 */
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bookable_type", nullable = false, length = 20)
    private BookableType bookableType;

    @Column(name = "bookable_id", nullable = false)
    private Long bookableId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }
}
