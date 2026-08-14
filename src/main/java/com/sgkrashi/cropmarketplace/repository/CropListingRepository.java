package com.sgkrashi.cropmarketplace.repository;

import com.sgkrashi.cropmarketplace.entity.CropListing;
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

public interface CropListingRepository extends JpaRepository<CropListing, Long>, JpaSpecificationExecutor<CropListing> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<CropListing> findAll(Specification<CropListing> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<CropListing> findByIdAndIsActiveTrue(Long id);

    /** Module 15 — Admin get-by-id, deliberately NOT scoped to isActive — see {@code ProductRepository.findWithCategoryById}'s Javadoc for why this needs its own @EntityGraph. */
    @EntityGraph(attributePaths = "category")
    Optional<CropListing> findWithCategoryById(Long id);

    @EntityGraph(attributePaths = "category")
    Optional<CropListing> findBySlugAndIsActiveTrue(String slug);

    @EntityGraph(attributePaths = "category")
    List<CropListing> findTop6ByCategoryIdAndIsActiveTrueAndIdNot(Long categoryId, Long excludedId);

    /** Same locking contract as {@code ProductRepository.findByIdForUpdate} — see its Javadoc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CropListing c where c.id = :id")
    Optional<CropListing> findByIdForUpdate(@Param("id") Long id);

    /** Uniqueness check for Module 15's Admin slug generation — see {@code ProductRepository.existsBySlug}'s Javadoc. */
    boolean existsBySlug(String slug);

    /** Module 20 — ownership check for Farmer-scoped get/update/delete. Deliberately returns empty (never throws) so callers can 404, not 403, on a listing that exists but isn't this farmer's — see {@code CustomerAddressRepository}'s Module 4 precedent. */
    Optional<CropListing> findByIdAndFarmerId(Long id, Long farmerId);

    long countByFarmerId(Long farmerId);

    long countByFarmerIdAndIsActiveTrue(Long farmerId);

    /** Recommendation System — "similar items" for crop listings. See {@code ProductRepository.findSimilarByCategoryAndPriceRange}'s Javadoc for the NULLS-last and price-band reasoning. */
    @Query("""
            SELECT c FROM CropListing c
            WHERE c.category.id = :categoryId
              AND c.id <> :excludedId
              AND c.isActive = true
              AND c.unitPrice BETWEEN :minPrice AND :maxPrice
            ORDER BY COALESCE(c.avgRating, 0) DESC, c.reviewCount DESC
            """)
    List<CropListing> findSimilarByCategoryAndPriceRange(
            @Param("categoryId") Long categoryId,
            @Param("excludedId") Long excludedId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    /** Recommendation System — "for you" seed. See {@code ProductRepository.findTopRatedInCategories}'s Javadoc for the excludeIds sentinel note. */
    @Query("""
            SELECT c FROM CropListing c
            WHERE c.isActive = true
              AND c.category.id IN :categoryIds
              AND c.id NOT IN :excludeIds
            ORDER BY COALESCE(c.avgRating, 0) DESC, c.reviewCount DESC
            """)
    List<CropListing> findTopRatedInCategories(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("excludeIds") List<Long> excludeIds,
            Pageable pageable);

    /** Recommendation System — "for you" fallback for no order history. See {@code ProductRepository.findTopRatedOverall}'s Javadoc. */
    @Query("""
            SELECT c FROM CropListing c
            WHERE c.isActive = true
              AND c.id NOT IN :excludeIds
            ORDER BY COALESCE(c.avgRating, 0) DESC, c.reviewCount DESC
            """)
    List<CropListing> findTopRatedOverall(@Param("excludeIds") List<Long> excludeIds, Pageable pageable);
}
