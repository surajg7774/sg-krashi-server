package com.sgkrashi.booking.specification;

import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** Same one-static-method-per-criterion shape as {@code ProductSpecifications}/{@code OrderSpecifications}. */
public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> hasStatus(BookingStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Booking> hasUserId(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
    }

    /** {@code dateFrom}/{@code dateTo} filter on {@code createdAt} (when the booking was made), not the stay/rental date range itself — matches Order's admin filter convention. */
    public static Specification<Booking> createdAfter(Instant dateFrom) {
        return (root, query, cb) -> dateFrom == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom);
    }

    public static Specification<Booking> createdBefore(Instant dateTo) {
        return (root, query, cb) -> dateTo == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), dateTo);
    }
}
