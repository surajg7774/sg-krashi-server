package com.sgkrashi.cropdoctor.rag.repository;

import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntry, Long> {

    List<KnowledgeBaseEntry> findByCropIgnoreCaseAndIsActiveTrueOrderByIdAsc(String crop);

    List<KnowledgeBaseEntry> findByIsActiveTrueOrderByCropAscIdAsc();
}
