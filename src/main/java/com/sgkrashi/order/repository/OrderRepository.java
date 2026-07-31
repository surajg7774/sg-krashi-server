package com.sgkrashi.order.repository;

import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    long countByUserId(Long userId);

    /** Admin dashboard KPI — today's orders by status (Module 14). {@code DELIVERED} is not a real state (see Module 12's gap note); only CONFIRMED/PAYMENT_FAILED/PENDING_PAYMENT are ever reachable. */
    long countByStatusAndCreatedAtBetween(OrderStatus status, Instant start, Instant end);
}
