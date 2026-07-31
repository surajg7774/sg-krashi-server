package com.sgkrashi.farmstay.repository;

import com.sgkrashi.farmstay.entity.StayListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StayListingRepository extends JpaRepository<StayListing, Long> {

    Page<StayListing> findByIsActiveTrue(Pageable pageable);

    /** Module 18 — Stay listings never had any public filter before (not even category); this is genuinely new. */
    Page<StayListing> findByIsActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    Optional<StayListing> findByIdAndIsActiveTrue(Long id);

    Optional<StayListing> findBySlugAndIsActiveTrue(String slug);

    /** Uniqueness check for Module 15's Admin slug generation — see {@code ProductRepository.existsBySlug}'s Javadoc. */
    boolean existsBySlug(String slug);

    /** Module 15 — Admin list, deliberately NOT scoped to isActive (see {@code ProductService.listProductsForAdmin}'s Javadoc). */
    Page<StayListing> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
