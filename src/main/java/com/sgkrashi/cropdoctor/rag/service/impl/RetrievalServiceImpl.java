package com.sgkrashi.cropdoctor.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.ai.embedding.CosineSimilarity;
import com.sgkrashi.ai.embedding.EmbeddingService;
import com.sgkrashi.ai.embedding.EmbeddingUnavailableException;
import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;
import com.sgkrashi.cropdoctor.rag.repository.KnowledgeBaseRepository;
import com.sgkrashi.cropdoctor.rag.service.RetrievalService;
import com.sgkrashi.cropdoctor.rag.service.ScoredKnowledgeBaseEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * V2 of retrieval: embedding-based cosine similarity as the primary path,
 * with the original V1 exact-crop-match as a fallback — never the other way
 * around, and never both blended into one ranked list, so which path
 * actually served a given scan stays unambiguous.
 *
 * <p>Semantic search fixes V1's one documented gap: an exact crop-tag match
 * can't connect "Chickpea" to a "Chana" entry, or "Corn" to "Maize", even
 * though they're the same crop under a different name. Comparing embeddings
 * of the query text against embeddings of each entry's content does, without
 * needing a synonym table someone has to maintain.
 *
 * <p>Falls back to {@link #metadataMatch} when: the embedding API call fails
 * (network/quota/timeout — {@link EmbeddingUnavailableException}, caught
 * here so a Gemini embedding outage degrades a scan's grounding quality, not
 * the scan itself), no entry has a stored embedding yet (mid-backfill, or a
 * freshly-added entry whose own embedding generation failed), or nothing
 * clears {@link #SIMILARITY_THRESHOLD}. This mirrors exactly how the rest of
 * this feature already treats "no relevant knowledge" — proceed without
 * grounding (or, here, with the cruder fallback) rather than forcing a
 * low-quality match through.
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    // Measured against this project's real 28-entry knowledge base
    // (gemini-embedding-001, taskType RETRIEVAL_DOCUMENT/RETRIEVAL_QUERY, 768
    // dimensions) via the /knowledge-base/search debug endpoint before this
    // was finalized — an initial guess of 0.55 turned out too low. Real
    // numbers: "Chickpea" scored 0.683-0.698 against the three Chana
    // entries; "Corn" scored 0.666-0.690 against the three Maize entries;
    // "Dragonfruit" (nothing in the knowledge base is actually about it)
    // topped out at 0.573 against its single closest entry. All agricultural
    // disease/management text sits in a fairly narrow semantic neighborhood
    // in this embedding space — even unrelated crops score 0.55-0.60 against
    // each other just from shared vocabulary/structure — so 0.55 let genuine
    // non-matches through. 0.62 sits with real margin below both matched
    // clusters and above the unrelated-query ceiling observed here.
    private static final double SIMILARITY_THRESHOLD = 0.62;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public RetrievalServiceImpl(
            KnowledgeBaseRepository knowledgeBaseRepository,
            EmbeddingService embeddingService,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<KnowledgeBaseEntry> retrieveForCrop(String declaredCrop, int maxResults) {
        if (declaredCrop == null || declaredCrop.isBlank()) {
            return List.of();
        }
        String query = declaredCrop.trim();

        List<ScoredKnowledgeBaseEntry> scored = scoreAllEntries(query);
        List<KnowledgeBaseEntry> semanticMatches = scored.stream()
                .filter(candidate -> candidate.similarity() >= SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredKnowledgeBaseEntry::similarity).reversed())
                .limit(maxResults)
                .map(ScoredKnowledgeBaseEntry::entry)
                .toList();
        if (!semanticMatches.isEmpty()) {
            return semanticMatches;
        }
        return metadataMatch(query, maxResults);
    }

    @Override
    public List<ScoredKnowledgeBaseEntry> semanticSearchWithScores(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return scoreAllEntries(query.trim()).stream()
                .sorted(Comparator.comparingDouble(ScoredKnowledgeBaseEntry::similarity).reversed())
                .limit(maxResults)
                .toList();
    }

    /** Every active, embedded entry scored against {@code query} — unfiltered, unsorted; callers apply their own threshold/ordering/limit. */
    private List<ScoredKnowledgeBaseEntry> scoreAllEntries(String query) {
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingService.embedQuery(query);
        } catch (EmbeddingUnavailableException ex) {
            log.warn("Semantic retrieval unavailable, falling back to metadata match: {}", ex.getMessage());
            return List.of();
        }

        return knowledgeBaseRepository.findByIsActiveTrueOrderByCropAscIdAsc().stream()
                .filter(entry -> entry.getEmbedding() != null)
                .map(entry -> new ScoredKnowledgeBaseEntry(entry, CosineSimilarity.compute(queryEmbedding, parseEmbedding(entry))))
                .toList();
    }

    private List<KnowledgeBaseEntry> metadataMatch(String declaredCrop, int maxResults) {
        List<KnowledgeBaseEntry> matches =
                knowledgeBaseRepository.findByCropIgnoreCaseAndIsActiveTrueOrderByIdAsc(declaredCrop);
        return matches.size() > maxResults ? matches.subList(0, maxResults) : matches;
    }

    private float[] parseEmbedding(KnowledgeBaseEntry entry) {
        try {
            return objectMapper.readValue(entry.getEmbedding(), float[].class);
        } catch (Exception ex) {
            // Malformed stored JSON should never happen (only this codebase
            // ever writes this column), but a scan is not the place to find
            // out — treat it the same as "no embedding" rather than throwing.
            log.warn("Could not parse stored embedding for knowledge base entry {}", entry.getId(), ex);
            return new float[0];
        }
    }

}
