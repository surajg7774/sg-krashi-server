package com.sgkrashi.cms.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One piece of Admin-editable public-site content (homepage hero banner,
 * testimonial, etc.) — replaces Module 3's hardcoded marketing copy.
 * {@code key} is a stable, human-meaningful identifier (e.g. {@code
 * "homepage_hero"}) the public frontend fetches by; {@code sortOrder} matters
 * for types with more than one active row at once (e.g. testimonials).
 * Edited in place, no versioning/history (Module 17 scope).
 */
@Entity
@Table(name = "content_blocks")
public class ContentBlock extends BaseEntity {

    @Column(name = "content_key", nullable = false, unique = true, length = 100)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ContentBlockType type;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public ContentBlockType getType() {
        return type;
    }

    public void setType(ContentBlockType type) {
        this.type = type;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
