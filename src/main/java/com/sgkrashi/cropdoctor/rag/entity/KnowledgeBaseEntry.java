package com.sgkrashi.cropdoctor.rag.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One retrievable passage of agricultural guidance — disease description,
 * treatment guidance, or general cultivation/prevention practice — used as
 * grounding context for {@code GeminiAnalysisProvider}'s RAG step. Deliberately
 * scoped to a single {@code crop} (never null): retrieval happens on the
 * user's declared crop alone (see {@code RetrievalService}'s Javadoc for why),
 * so an entry with no crop would never be reachable by that query anyway.
 */
@Entity
@Table(name = "knowledge_base_entries")
public class KnowledgeBaseEntry extends BaseEntity {

    @Column(name = "crop", nullable = false, length = 100)
    private String crop;

    @Column(name = "topic", nullable = false, length = 150)
    private String topic;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // JSON-serialized float array (Gemini's embedContent output), NOT a
    // native MySQL vector type — this project's MySQL version has none, and
    // at this knowledge base's scale (a few dozen entries) comparing stored
    // arrays in the application layer is simpler and fast enough that a
    // vector column/index would be over-engineering. Null until
    // KnowledgeBaseEmbeddingBackfillRunner or KnowledgeBaseIngestionServiceImpl
    // populates it — RetrievalServiceImpl skips entries with no embedding
    // when doing semantic search, falling back to the metadata-match path
    // if that leaves nothing to compare against.
    @Column(name = "embedding", columnDefinition = "JSON")
    private String embedding;

    @Column(name = "source", nullable = false, length = 300)
    private String source;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    public String getCrop() {
        return crop;
    }

    public void setCrop(String crop) {
        this.crop = crop;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
