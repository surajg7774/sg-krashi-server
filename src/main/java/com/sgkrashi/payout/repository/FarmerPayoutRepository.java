package com.sgkrashi.payout.repository;

import com.sgkrashi.payout.entity.FarmerPayout;
import com.sgkrashi.payout.entity.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmerPayoutRepository extends JpaRepository<FarmerPayout, Long> {

    Page<FarmerPayout> findByFarmerIdOrderByCreatedAtDesc(Long farmerId, Pageable pageable);

    /** Ownership check for Farmer-scoped detail — deliberately empty (never throws) so callers can 404, not 403. Same convention as {@code CropListingRepository.findByIdAndFarmerId}. */
    Optional<FarmerPayout> findByIdAndFarmerId(Long id, Long farmerId);

    Page<FarmerPayout> findByStatusOrderByCreatedAtAsc(PayoutStatus status, Pageable pageable);

    /**
     * A farmer's currently-open batch, if one exists — at most one BATCHED
     * payout should ever exist per farmer at a time, but {@code findFirst}
     * is a defensive read rather than assuming that invariant always holds.
     * Both the weekly job and a refund clawback reuse this same open batch
     * instead of creating a second one; see {@code PayoutService.getOrCreateOpenBatch}.
     */
    Optional<FarmerPayout> findFirstByFarmerIdAndStatusOrderByIdDesc(Long farmerId, PayoutStatus status);
}
