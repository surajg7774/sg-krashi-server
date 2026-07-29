package com.sgkrashi.order.repository;

import com.sgkrashi.order.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = {"product"})
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Same rows as {@link #findByOrderId}, but WITHOUT eagerly loading the
     * {@code product} association — see {@code CartItemRepository.findAllByCartId}'s
     * Javadoc for why this matters whenever a pessimistic-lock fetch on that
     * same product follows (as in {@code OrderServiceImpl.markPaymentFailed}).
     */
    List<OrderItem> findAllByOrderId(Long orderId);

    long countByOrderId(Long orderId);
}
