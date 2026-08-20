package com.sgkrashi.chatassistant.knowledge.service;

import com.sgkrashi.chatassistant.knowledge.entity.PlatformKnowledgeEntry;

import java.util.List;

/**
 * The platform-FAQ analogue of {@code com.sgkrashi.cropdoctor.rag.service.RetrievalService}
 * — same embedding + cosine-similarity mechanism (reuses {@code
 * com.sgkrashi.ai.embedding.EmbeddingService}/{@code CosineSimilarity}
 * directly), but no metadata-match fallback: crop retrieval could fall back
 * to an exact {@code crop} tag because the query (the user's declared crop)
 * is itself a clean tag-shaped value. A chat message isn't — "how do I
 * cancel my farm stay booking" has no single exact tag to fall back to — so
 * when nothing clears the similarity threshold here, the caller ({@code
 * ChatServiceImpl}) just proceeds without grounding, the same honest
 * degradation the crop doctor uses when its own fallback also finds nothing.
 */
public interface PlatformKnowledgeService {

    List<PlatformKnowledgeEntry> retrieve(String query, int maxResults);

    /** Debug/transparency only — every scored candidate, threshold not applied. Backs the search debug endpoint. */
    List<ScoredPlatformKnowledgeEntry> searchWithScores(String query, int maxResults);

    /** Generates and stores an embedding for every active entry that doesn't have one yet — see {@code PlatformKnowledgeEmbeddingBackfillRunner}. */
    void backfillMissingEmbeddings();
}
