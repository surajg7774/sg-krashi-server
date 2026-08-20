package com.sgkrashi.cropdoctor.rag.service.impl;

import com.sgkrashi.cropdoctor.rag.service.KnowledgeBaseIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once per boot, after the app is already accepting traffic (Tomcat
 * starts during context refresh, before {@link ApplicationRunner}s execute —
 * this doesn't delay {@code /health} or anything else). A one-time embedding
 * backfill needs to run exactly once for the 28 entries seeded before
 * embedding-based retrieval existed; rather than a separate admin-triggered
 * endpoint someone has to remember to call, this makes it self-running and
 * self-limiting — {@code backfillMissingEmbeddings} only ever touches rows
 * still missing an embedding, so every boot after the first is a fast no-op
 * single query.
 */
@Component
public class KnowledgeBaseEmbeddingBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseEmbeddingBackfillRunner.class);

    private final KnowledgeBaseIngestionService knowledgeBaseIngestionService;

    public KnowledgeBaseEmbeddingBackfillRunner(KnowledgeBaseIngestionService knowledgeBaseIngestionService) {
        this.knowledgeBaseIngestionService = knowledgeBaseIngestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            knowledgeBaseIngestionService.backfillMissingEmbeddings();
        } catch (Exception ex) {
            // Never fail application startup over this — a missing embedding
            // just means that entry falls back to metadata matching until a
            // later boot succeeds.
            log.warn("Knowledge base embedding backfill failed, will retry next boot", ex);
        }
    }
}
