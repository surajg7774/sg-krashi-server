package com.sgkrashi.recommendation.repository;

import com.sgkrashi.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Extends {@code JpaRepository<OrderItem, Long>} purely as Spring Data's
 * required base type, same convention as {@code AnalyticsQueryRepository} —
 * every method below is a real SQL-level {@code GROUP BY}/{@code DISTINCT}
 * query over existing order history, never entity-list aggregation in Java.
 */
public interface RecommendationQueryRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Item-based collaborative filtering ("customers who bought this also
     * bought") — a co-occurrence count, not a trained model: for the given
     * product, counts how many distinct completed orders also contained each
     * OTHER product, ranked by that count. This IS the whole technique.
     */
    @Query(value = """
            SELECT oi2.product_id AS productId, COUNT(DISTINCT oi1.order_id) AS coOccurrenceCount
            FROM order_items oi1
            JOIN order_items oi2 ON oi2.order_id = oi1.order_id AND oi2.product_id <> oi1.product_id
            JOIN orders o ON o.id = oi1.order_id
            WHERE oi1.product_id = :productId
              AND oi1.item_type = 'PRODUCT' AND oi2.item_type = 'PRODUCT'
              AND o.status IN ('CONFIRMED', 'REFUNDED')
            GROUP BY oi2.product_id
            ORDER BY coOccurrenceCount DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findFrequentlyBoughtWithProductIds(@Param("productId") Long productId, @Param("limit") int limit);

    /** Distinct product categories this user has actually purchased from — the "signal" behind /for-you. */
    @Query(value = """
            SELECT DISTINCT p.category_id
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN products p ON p.id = oi.product_id
            WHERE o.user_id = :userId AND oi.item_type = 'PRODUCT' AND o.status IN ('CONFIRMED', 'REFUNDED')
            """, nativeQuery = true)
    List<Long> findPurchasedProductCategoryIds(@Param("userId") Long userId);

    /** Same idea as {@link #findPurchasedProductCategoryIds}, for crop listings. */
    @Query(value = """
            SELECT DISTINCT c.crop_category_id
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN crop_listings c ON c.id = oi.crop_listing_id
            WHERE o.user_id = :userId AND oi.item_type = 'CROP_LISTING' AND o.status IN ('CONFIRMED', 'REFUNDED')
            """, nativeQuery = true)
    List<Long> findPurchasedCropCategoryIds(@Param("userId") Long userId);

    /** Product ids this user already owns — excluded from /for-you so it never recommends what they already bought. */
    @Query(value = """
            SELECT DISTINCT oi.product_id
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.user_id = :userId AND oi.item_type = 'PRODUCT' AND o.status IN ('CONFIRMED', 'REFUNDED')
            """, nativeQuery = true)
    List<Long> findPurchasedProductIds(@Param("userId") Long userId);

    /** Same idea as {@link #findPurchasedProductIds}, for crop listings. */
    @Query(value = """
            SELECT DISTINCT oi.crop_listing_id
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.user_id = :userId AND oi.item_type = 'CROP_LISTING' AND o.status IN ('CONFIRMED', 'REFUNDED')
            """, nativeQuery = true)
    List<Long> findPurchasedCropListingIds(@Param("userId") Long userId);
}
