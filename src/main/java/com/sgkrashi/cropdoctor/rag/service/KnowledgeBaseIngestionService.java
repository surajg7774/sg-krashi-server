package com.sgkrashi.cropdoctor.rag.service;

import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;

import java.util.List;

/**
 * The write/management side of the knowledge base — separate from {@link
 * RetrievalService} (the read path {@code GeminiAnalysisProvider} actually
 * calls per scan) on purpose, so retrieval logic never has to know how
 * content got into the table. V1's real content is seeded via Flyway
 * migration (same convention as every other module's seed data in this
 * project — crop listings, products, categories), not through this service
 * at startup; this exists as the genuine extension point a future Admin
 * content-management UI (explicitly out of scope for V1) would call, and is
 * what backs the debug listing endpoint today.
 */
public interface KnowledgeBaseIngestionService {

    KnowledgeBaseEntry create(String crop, String topic, String title, String content, String source, String language);

    /** {@code crop}/{@code topic} are optional filters — both null returns every active entry. */
    List<KnowledgeBaseEntry> list(String crop, String topic);

    /**
     * Generates and stores an embedding for every active entry that doesn't
     * have one yet — the one-time catch-up for entries seeded before
     * embedding-based retrieval existed. Called by {@code
     * KnowledgeBaseEmbeddingBackfillRunner} at application startup; safe to
     * call repeatedly, since it only ever touches rows with a null {@code
     * embedding} column.
     */
    void backfillMissingEmbeddings();
}
