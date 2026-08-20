package com.sgkrashi.chatassistant.knowledge.service.impl;

import com.sgkrashi.chatassistant.knowledge.service.PlatformKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Same self-running, self-limiting backfill pattern as {@code com.sgkrashi.cropdoctor.rag.service.impl.KnowledgeBaseEmbeddingBackfillRunner} — see its Javadoc. */
@Component
public class PlatformKnowledgeEmbeddingBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformKnowledgeEmbeddingBackfillRunner.class);

    private final PlatformKnowledgeService platformKnowledgeService;

    public PlatformKnowledgeEmbeddingBackfillRunner(PlatformKnowledgeService platformKnowledgeService) {
        this.platformKnowledgeService = platformKnowledgeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            platformKnowledgeService.backfillMissingEmbeddings();
        } catch (Exception ex) {
            log.warn("Platform knowledge embedding backfill failed, will retry next boot", ex);
        }
    }
}
