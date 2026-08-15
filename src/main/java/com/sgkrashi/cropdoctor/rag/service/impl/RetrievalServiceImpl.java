package com.sgkrashi.cropdoctor.rag.service.impl;

import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;
import com.sgkrashi.cropdoctor.rag.repository.KnowledgeBaseRepository;
import com.sgkrashi.cropdoctor.rag.service.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Option 1 from the RAG task spec: plain metadata (crop) filtering against
 * MySQL, no embeddings, no vector database. At this knowledge base's real
 * scale (a few dozen curated entries, not the few-hundred-to-low-thousands
 * scale where embedding similarity would start to earn its complexity), an
 * exact crop match is just as legitimate a retrieval strategy and is
 * trivially explainable — there is no ambiguity to resolve with cosine
 * similarity when the query is "give me what's tagged for this crop."
 * Embedding-based semantic retrieval remains a reasonable follow-up (see the
 * task's own section 3) once the knowledge base grows large/varied enough
 * that exact crop tags stop being a precise enough filter on their own.
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public RetrievalServiceImpl(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Override
    public List<KnowledgeBaseEntry> retrieveForCrop(String declaredCrop, int maxResults) {
        if (declaredCrop == null || declaredCrop.isBlank()) {
            return List.of();
        }
        List<KnowledgeBaseEntry> matches =
                knowledgeBaseRepository.findByCropIgnoreCaseAndIsActiveTrueOrderByIdAsc(declaredCrop.trim());
        return matches.size() > maxResults ? matches.subList(0, maxResults) : matches;
    }
}
