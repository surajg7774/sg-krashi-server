package com.sgkrashi.cropdoctor.rag.service.impl;

import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;
import com.sgkrashi.cropdoctor.rag.repository.KnowledgeBaseRepository;
import com.sgkrashi.cropdoctor.rag.service.KnowledgeBaseIngestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseIngestionServiceImpl implements KnowledgeBaseIngestionService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseIngestionServiceImpl(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
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
        return knowledgeBaseRepository.save(entry);
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
