package com.sgkrashi.order.repository;

import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Row lock for every status change, so two simultaneous changes to one
     * order (an admin double-click, or the payment webhook racing an admin)
     * are serialized: the second re-reads the new status and is then a no-op
     * or a clear "invalid transition" instead of a duplicate history row and
     * a duplicate push.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    long countByUserId(Long userId);

    /** Admin dashboard KPI — today's orders in a single given status (Module 14). */
    long countByStatusAndCreatedAtBetween(OrderStatus status, Instant start, Instant end);

    /**
     * Same KPI as {@link #countByStatusAndCreatedAtBetween}, but across
     * several statuses at once — used for "today's successfully-paid orders",
     * which now spans both CONFIRMED and (once an admin has marked it)
     * SHIPPED/DELIVERED, since a shipped or delivered order was just as genuinely paid today.
     */
    long countByStatusInAndCreatedAtBetween(List<OrderStatus> statuses, Instant start, Instant end);
}
