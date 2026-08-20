package com.sgkrashi.chatassistant.knowledge.service;

import com.sgkrashi.chatassistant.knowledge.entity.PlatformKnowledgeEntry;

/** Debug/transparency use only — see {@code com.sgkrashi.cropdoctor.rag.service.ScoredKnowledgeBaseEntry}, same purpose for the platform knowledge base. */
public record ScoredPlatformKnowledgeEntry(PlatformKnowledgeEntry entry, double similarity) {
}
