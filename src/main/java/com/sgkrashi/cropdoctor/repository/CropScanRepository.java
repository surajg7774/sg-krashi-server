package com.sgkrashi.cropdoctor.repository;

import com.sgkrashi.cropdoctor.entity.CropScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropScanRepository extends JpaRepository<CropScan, Long> {

    Page<CropScan> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
