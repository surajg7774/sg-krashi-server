package com.sgkrashi.chatassistant.knowledge.repository;

import com.sgkrashi.chatassistant.knowledge.entity.PlatformKnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformKnowledgeRepository extends JpaRepository<PlatformKnowledgeEntry, Long> {

    List<PlatformKnowledgeEntry> findByIsActiveTrueOrderByCategoryAscIdAsc();

    List<PlatformKnowledgeEntry> findByEmbeddingIsNullAndIsActiveTrue();
}
