package com.sgkrashi.productstore.repository;

import com.sgkrashi.productstore.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // @EntityGraph eagerly joins category in the same query, rather than
    // leaving it lazy — avoids both a LazyInitializationException once the
    // session closes (mapping happens after the repository call returns) and
    // an N+1 query per row when the mapper reads category.getName().

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndIsActiveTrue(Long id);

    /** Module 15 — Admin get-by-id, deliberately NOT scoped to isActive (a deactivated product must still be viewable/editable). Needs its own @EntityGraph since the plain inherited findById leaves category lazy, unlike every other lookup here. */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndIsActiveTrue(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findTop6ByCategoryIdAndIsActiveTrueAndIdNot(Long categoryId, Long excludedId);

    /**
     * Locks the row for the duration of the caller's transaction (SELECT ... FOR UPDATE).
     * Used only at checkout, where callers MUST fetch products in ascending ID order
     * across the whole cart before acquiring any lock, to avoid deadlocking against a
     * concurrent checkout that references the same products in a different order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /** Admin dashboard KPI — simple threshold query against the existing table, no materialized view (Year 1 scale per the architecture doc). */
    long countByStockQtyLessThanAndIsActiveTrue(int threshold);

    /** Uniqueness check for Module 15's Admin slug generation — deliberately NOT scoped to isActive, so a new slug can't collide with a soft-deleted product's. */
    boolean existsBySlug(String slug);

    /** Predictive Analytics — every active product, for the stock-risk sweep (RecommendationService/ForecastService's own read, not a dashboard KPI). */
    List<Product> findByIsActiveTrue();

    /**
     * Recommendation System — "similar items" (content-based): same category
     * and a similar price band, ranked by rating then review count.
     * {@code COALESCE(..., 0)} sorts not-yet-rated products last rather than
     * first, which plain {@code ORDER BY avg_rating DESC} would do since NULL
     * sorts first in MySQL's default DESC ordering.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.category.id = :categoryId
              AND p.id <> :excludedId
              AND p.isActive = true
              AND p.price BETWEEN :minPrice AND :maxPrice
            ORDER BY COALESCE(p.avgRating, 0) DESC, p.reviewCount DESC
            """)
    List<Product> findSimilarByCategoryAndPriceRange(
            @Param("categoryId") Long categoryId,
            @Param("excludedId") Long excludedId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    /** Recommendation System — "for you", seeded by the categories a customer has actually purchased from. {@code excludeIds} must be non-empty (a sentinel like {@code [-1]}) — an empty JPQL IN-list is invalid. */
    @Query("""
            SELECT p FROM Product p
            WHERE p.isActive = true
              AND p.category.id IN :categoryIds
              AND p.id NOT IN :excludeIds
            ORDER BY COALESCE(p.avgRating, 0) DESC, p.reviewCount DESC
            """)
    List<Product> findTopRatedInCategories(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("excludeIds") List<Long> excludeIds,
            Pageable pageable);

    /** Recommendation System — "for you" fallback for a customer with no order history at all (see {@link #findTopRatedInCategories}'s excludeIds note). */
    @Query("""
            SELECT p FROM Product p
            WHERE p.isActive = true
              AND p.id NOT IN :excludeIds
            ORDER BY COALESCE(p.avgRating, 0) DESC, p.reviewCount DESC
            """)
    List<Product> findTopRatedOverall(@Param("excludeIds") List<Long> excludeIds, Pageable pageable);
}
