package com.sgkrashi.chatassistant.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.ai.embedding.CosineSimilarity;
import com.sgkrashi.ai.embedding.EmbeddingService;
import com.sgkrashi.ai.embedding.EmbeddingUnavailableException;
import com.sgkrashi.chatassistant.knowledge.entity.PlatformKnowledgeEntry;
import com.sgkrashi.chatassistant.knowledge.repository.PlatformKnowledgeRepository;
import com.sgkrashi.chatassistant.knowledge.service.PlatformKnowledgeService;
import com.sgkrashi.chatassistant.knowledge.service.ScoredPlatformKnowledgeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class PlatformKnowledgeServiceImpl implements PlatformKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(PlatformKnowledgeServiceImpl.class);

    // Measured against this project's real 26-entry platform knowledge base
    // via /chat/knowledge-base/search before this was finalized — same
    // methodology as AI Crop Doctor's RetrievalServiceImpl (see its comment).
    // Real numbers: "farm stay cancellation" scored 0.793/0.732 against the
    // Farm Stay/Refunds entries; "how do refunds work" scored 0.719/0.703
    // against the Refunds entries; a deliberately unrelated query ("what is
    // the weather today") topped out at 0.545. Unlike the crop knowledge
    // base (where an initial 0.55 guess had to be corrected to 0.62), 0.60
    // sits cleanly in the real gap here on the first try — platform-FAQ
    // content apparently separates more distinctly in this embedding space
    // than crop-disease text does.
    private static final double SIMILARITY_THRESHOLD = 0.60;

    private final PlatformKnowledgeRepository platformKnowledgeRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public PlatformKnowledgeServiceImpl(
            PlatformKnowledgeRepository platformKnowledgeRepository,
            EmbeddingService embeddingService,
            ObjectMapper objectMapper
    ) {
        this.platformKnowledgeRepository = platformKnowledgeRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PlatformKnowledgeEntry> retrieve(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return scoreAllEntries(query.trim()).stream()
                .filter(scored -> scored.similarity() >= SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredPlatformKnowledgeEntry::similarity).reversed())
                .limit(maxResults)
                .map(ScoredPlatformKnowledgeEntry::entry)
                .toList();
    }

    @Override
    public List<ScoredPlatformKnowledgeEntry> searchWithScores(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return scoreAllEntries(query.trim()).stream()
                .sorted(Comparator.comparingDouble(ScoredPlatformKnowledgeEntry::similarity).reversed())
                .limit(maxResults)
                .toList();
    }

    private List<ScoredPlatformKnowledgeEntry> scoreAllEntries(String query) {
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingService.embedQuery(query);
        } catch (EmbeddingUnavailableException ex) {
            log.warn("Platform knowledge retrieval unavailable: {}", ex.getMessage());
            return List.of();
        }

        return platformKnowledgeRepository.findByIsActiveTrueOrderByCategoryAscIdAsc().stream()
                .filter(entry -> entry.getEmbedding() != null)
                .map(entry -> new ScoredPlatformKnowledgeEntry(entry, CosineSimilarity.compute(queryEmbedding, parseEmbedding(entry))))
                .toList();
    }

    @Override
    @Transactional
    public void backfillMissingEmbeddings() {
        List<PlatformKnowledgeEntry> pending = platformKnowledgeRepository.findByEmbeddingIsNullAndIsActiveTrue();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Backfilling embeddings for {} platform knowledge entries", pending.size());
        int failures = 0;
        for (PlatformKnowledgeEntry entry : pending) {
            try {
                float[] embedding = embeddingService.embedDocument(entry.getTitle() + "\n" + entry.getContent());
                entry.setEmbedding(objectMapper.writeValueAsString(embedding));
                platformKnowledgeRepository.save(entry);
            } catch (Exception ex) {
                failures++;
                log.warn("Failed to generate embedding for platform knowledge entry '{}': {}", entry.getTitle(), ex.getMessage());
            }
        }
        log.info("Platform knowledge embedding backfill complete: {} succeeded, {} failed (will retry next boot)",
                pending.size() - failures, failures);
    }

    private float[] parseEmbedding(PlatformKnowledgeEntry entry) {
        try {
            return objectMapper.readValue(entry.getEmbedding(), float[].class);
        } catch (Exception ex) {
            log.warn("Could not parse stored embedding for platform knowledge entry {}", entry.getId(), ex);
            return new float[0];
        }
    }
}
