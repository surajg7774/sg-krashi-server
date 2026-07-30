package com.sgkrashi.review.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A Customer's rating + comment for a Product, Crop Listing, Equipment or
 * Stay, tied to the specific completed transaction that made them eligible
 * ({@link #orderItemId} for Product/CropListing, {@link #bookingId} for
 * Equipment/Stay — exactly one populated, depending on {@link #targetType}).
 *
 * <p>Same polymorphic-lite tradeoff as {@code MediaAsset.ownerId}: {@link
 * #targetId} has no foreign key, since it points at a different table
 * depending on {@link #targetType} and no single FK could express that. The
 * same tradeoff applies: a dangling target_id after a hard delete would be
 * orphaned, but every target entity in this codebase is soft-deleted
 * (is_active = false), so that shouldn't occur in practice.
 *
 * <p>Once posted, a review is immutable — no edit/delete path exists by
 * design (see Module 12's explicit scope).
 */
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ReviewTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** Populated only when targetType is PRODUCT/CROP_LISTING — the OrderItem that made this review eligible. */
    @Column(name = "order_item_id")
    private Long orderItemId;

    /** Populated only when targetType is EQUIPMENT/STAY — the Booking that made this review eligible. */
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
    private String comment;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ReviewTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(ReviewTargetType targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
