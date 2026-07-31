package com.sgkrashi.order.specification;

import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** Same one-static-method-per-criterion shape as {@code ProductSpecifications} (Module 5/15). */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasUserId(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
    }

    /** {@code dateFrom}/{@code dateTo} filter on {@code createdAt} (when the order was placed) — Order has no other meaningful date field. */
    public static Specification<Order> createdAfter(Instant dateFrom) {
        return (root, query, cb) -> dateFrom == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom);
    }

    public static Specification<Order> createdBefore(Instant dateTo) {
        return (root, query, cb) -> dateTo == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), dateTo);
    }
}
