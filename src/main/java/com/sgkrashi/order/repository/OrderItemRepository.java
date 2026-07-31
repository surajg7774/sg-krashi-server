package com.sgkrashi.order.repository;

import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.order.entity.OrderItem;
import com.sgkrashi.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = {"product", "cropListing"})
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Same rows as {@link #findByOrderId}, but WITHOUT eagerly loading the
     * {@code product}/{@code cropListing} associations — see
     * {@code CartItemRepository.findAllByCartId}'s Javadoc for why this matters
     * whenever a pessimistic-lock fetch on that same row follows (as in
     * {@code OrderServiceImpl.markPaymentFailed}).
     */
    List<OrderItem> findAllByOrderId(Long orderId);

    long countByOrderId(Long orderId);

    /**
     * Candidate order items for Module 12's review eligibility check: this
     * user's own order items, of the given type, referencing {@code
     * targetId}, on an order in {@code status}. {@code product.id =
     * :targetId or cropListing.id = :targetId} is safe despite checking both
     * columns — {@code itemType} is already filtered, and exactly one of the
     * two FKs is ever populated per that type (same invariant as {@code
     * OrderItem.getReferencedItemId}).
     */
    @Query("""
            select oi from OrderItem oi
            join oi.order o
            where o.userId = :userId
              and o.status = :status
              and oi.itemType = :itemType
              and (oi.product.id = :targetId or oi.cropListing.id = :targetId)
            order by o.createdAt desc
            """)
    List<OrderItem> findEligibleForReview(
            @Param("userId") Long userId,
            @Param("status") OrderStatus status,
            @Param("itemType") ItemType itemType,
            @Param("targetId") Long targetId
    );

    /** Same eligibility rule as {@link #findEligibleForReview}, scoped to one specific claimed order item — used to re-verify a review submission's claimed transaction. */
    @Query("""
            select oi from OrderItem oi
            join oi.order o
            where oi.id = :orderItemId
              and o.userId = :userId
              and o.status = :status
              and oi.itemType = :itemType
              and (oi.product.id = :targetId or oi.cropListing.id = :targetId)
            """)
    Optional<OrderItem> findEligibleOrderItemById(
            @Param("orderItemId") Long orderItemId,
            @Param("userId") Long userId,
            @Param("status") OrderStatus status,
            @Param("itemType") ItemType itemType,
            @Param("targetId") Long targetId
    );

    /**
     * Module 20 — Farmer dashboard stats. Plain JPQL (not native SQL) suffices
     * here, unlike Module 19's analytics queries: there's no date-bucketing
     * function involved, just a straight aggregate over a join, so Spring
     * Data can return a real {@code long} directly with no Object[]/casting.
     */
    @Query("""
            select count(distinct oi.order.id) from OrderItem oi
            where oi.cropListing.farmerId = :farmerId and oi.order.status in :statuses
            """)
    long countDistinctOrdersByFarmerId(@Param("farmerId") Long farmerId, @Param("statuses") List<OrderStatus> statuses);

    /** Companion to {@link #countDistinctOrdersByFarmerId} — total units across those same order items. */
    @Query("""
            select coalesce(sum(oi.quantity), 0) from OrderItem oi
            where oi.cropListing.farmerId = :farmerId and oi.order.status in :statuses
            """)
    long sumQuantityByFarmerId(@Param("farmerId") Long farmerId, @Param("statuses") List<OrderStatus> statuses);
}
