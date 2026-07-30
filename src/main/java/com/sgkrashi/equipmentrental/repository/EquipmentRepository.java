package com.sgkrashi.equipmentrental.repository;

import com.sgkrashi.equipmentrental.entity.Equipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Page<Equipment> findByIsActiveTrue(Pageable pageable);

    Page<Equipment> findByIsActiveTrueAndCategoryIgnoreCase(String category, Pageable pageable);

    Optional<Equipment> findByIdAndIsActiveTrue(Long id);

    Optional<Equipment> findBySlugAndIsActiveTrue(String slug);

    @Query("select distinct e.category from Equipment e where e.isActive = true order by e.category asc")
    List<String> findDistinctCategories();

    /** Uniqueness check for Module 15's Admin slug generation — see {@code ProductRepository.existsBySlug}'s Javadoc. */
    boolean existsBySlug(String slug);

    /** Module 15 — Admin list, deliberately NOT scoped to isActive (see {@code ProductService.listProductsForAdmin}'s Javadoc). */
    Page<Equipment> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
