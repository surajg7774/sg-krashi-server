package com.sgkrashi.cropdoctor.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;
import com.sgkrashi.cropdoctor.rag.repository.KnowledgeBaseRepository;
import com.sgkrashi.cropdoctor.rag.service.EmbeddingService;
import com.sgkrashi.cropdoctor.rag.service.KnowledgeBaseIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseIngestionServiceImpl implements KnowledgeBaseIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestionServiceImpl.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseIngestionServiceImpl(
            KnowledgeBaseRepository knowledgeBaseRepository,
            EmbeddingService embeddingService,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public KnowledgeBaseEntry create(String crop, String topic, String title, String content, String source, String language) {
        KnowledgeBaseEntry entry = new KnowledgeBaseEntry();
        entry.setCrop(crop);
        entry.setTopic(topic);
        entry.setTitle(title);
        entry.setContent(content);
        entry.setSource(source);
        entry.setLanguage(language == null || language.isBlank() ? "en" : language);
        entry.setEmbedding(generateEmbeddingJson(entry));
        return knowledgeBaseRepository.save(entry);
    }

    @Override
    @Transactional
    public void backfillMissingEmbeddings() {
        List<KnowledgeBaseEntry> pending = knowledgeBaseRepository.findByEmbeddingIsNullAndIsActiveTrue();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Backfilling embeddings for {} knowledge base entries", pending.size());
        int failures = 0;
        for (KnowledgeBaseEntry entry : pending) {
            String embeddingJson = generateEmbeddingJson(entry);
            if (embeddingJson == null) {
                failures++;
                continue;
            }
            entry.setEmbedding(embeddingJson);
            knowledgeBaseRepository.save(entry);
        }
        log.info("Knowledge base embedding backfill complete: {} succeeded, {} failed (will retry next boot)",
                pending.size() - failures, failures);
    }

    /**
     * Never throws — a knowledge base entry is still fully usable via the
     * metadata-match fallback with no embedding at all (see {@code
     * RetrievalServiceImpl}), so a transient Gemini failure here shouldn't
     * block creating the entry or crash the backfill loop. Returns null on
     * failure; caller stores that as "still missing," which both {@code
     * create} and {@link #backfillMissingEmbeddings} correctly leave to be
     * retried later (by the next backfill run, or a manual re-save).
     */
    private String generateEmbeddingJson(KnowledgeBaseEntry entry) {
        try {
            float[] embedding = embeddingService.embedDocument(entry.getTitle() + "\n" + entry.getContent());
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception ex) {
            log.warn("Failed to generate embedding for knowledge base entry '{}': {}", entry.getTitle(), ex.getMessage());
            return null;
        }
    }

    @Override
    public List<KnowledgeBaseEntry> list(String crop, String topic) {
        List<KnowledgeBaseEntry> entries = crop == null || crop.isBlank()
                ? knowledgeBaseRepository.findByIsActiveTrueOrderByCropAscIdAsc()
                : knowledgeBaseRepository.findByCropIgnoreCaseAndIsActiveTrueOrderByIdAsc(crop.trim());

        if (topic == null || topic.isBlank()) {
            return entries;
        }
        String topicFilter = topic.trim().toLowerCase();
        return entries.stream()
                .filter(entry -> entry.getTopic().toLowerCase().contains(topicFilter))
                .toList();
    }
}
