package com.sgkrashi.scheme.repository;

import com.sgkrashi.scheme.entity.Scheme;
import com.sgkrashi.scheme.entity.SchemeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchemeRepository extends JpaRepository<Scheme, Long> {

    List<Scheme> findByIsActiveTrueAndCategoryOrderBySortOrderAsc(SchemeCategory category);

    List<Scheme> findByIsActiveTrueOrderBySortOrderAsc();

    List<Scheme> findAllByOrderBySortOrderAsc();
}
