package com.sgkrashi.cms.repository;

import com.sgkrashi.cms.entity.ContentBlock;
import com.sgkrashi.cms.entity.ContentBlockType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentBlockRepository extends JpaRepository<ContentBlock, Long> {

    List<ContentBlock> findByIsActiveTrueOrderBySortOrderAsc();

    List<ContentBlock> findByTypeAndIsActiveTrueOrderBySortOrderAsc(ContentBlockType type);

    List<ContentBlock> findAllByOrderBySortOrderAsc();

    Optional<ContentBlock> findByKey(String key);

    boolean existsByKey(String key);
}
