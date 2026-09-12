package com.sgkrashi.order.repository;

import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    long countByUserId(Long userId);

    /** Admin dashboard KPI — today's orders in a single given status (Module 14). */
    long countByStatusAndCreatedAtBetween(OrderStatus status, Instant start, Instant end);

    /**
     * Same KPI as {@link #countByStatusAndCreatedAtBetween}, but across
     * several statuses at once — used for "today's successfully-paid orders",
     * which now spans both CONFIRMED and (once an admin has marked it)
     * DELIVERED, since a delivered order was just as genuinely paid today.
     */
    long countByStatusInAndCreatedAtBetween(List<OrderStatus> statuses, Instant start, Instant end);
}
