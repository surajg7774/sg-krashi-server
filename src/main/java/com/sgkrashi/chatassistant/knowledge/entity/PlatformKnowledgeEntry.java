package com.sgkrashi.chatassistant.knowledge.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One retrievable passage of platform documentation (how a business line
 * works, a policy, an account how-to) — the chat assistant's grounding
 * source for general platform Q&A, exactly analogous to {@code
 * com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry} for crop-disease
 * content. Deliberately a separate table/entity rather than reusing that one
 * with a domain discriminator column: the two knowledge bases serve
 * unrelated domains (crop pathology vs. platform documentation), have
 * different natural metadata ({@code crop} makes no sense here; {@code
 * category} makes no sense there), and mixing them would make every query
 * against either need an extra filter to avoid cross-domain results leaking
 * into retrieval for the other.
 */
@Entity
@Table(name = "platform_knowledge_entries")
public class PlatformKnowledgeEntry extends BaseEntity {

    @Column(name = "category", nullable = false, length = 150)
    private String category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // JSON-serialized float array — see KnowledgeBaseEntry.embedding's
    // comment for why (no native vector type at this project's MySQL
    // version; not needed at this knowledge base's scale either).
    @Column(name = "embedding", columnDefinition = "JSON")
    private String embedding;

    @Column(name = "source", nullable = false, length = 300)
    private String source;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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
}
