package com.sgkrashi.farmstay.repository;

import com.sgkrashi.farmstay.entity.StayListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StayListingRepository extends JpaRepository<StayListing, Long> {

    Page<StayListing> findByIsActiveTrue(Pageable pageable);

    Optional<StayListing> findByIdAndIsActiveTrue(Long id);

    Optional<StayListing> findBySlugAndIsActiveTrue(String slug);
}
