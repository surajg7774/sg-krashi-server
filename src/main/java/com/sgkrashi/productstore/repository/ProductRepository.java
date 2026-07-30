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
}
