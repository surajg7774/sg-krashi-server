package com.sgkrashi.cropmarketplace.repository;

import com.sgkrashi.cropmarketplace.entity.CropCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CropCategoryRepository extends JpaRepository<CropCategory, Long> {

    List<CropCategory> findByIsActiveTrue();

    Optional<CropCategory> findBySlugAndIsActiveTrue(String slug);
}
