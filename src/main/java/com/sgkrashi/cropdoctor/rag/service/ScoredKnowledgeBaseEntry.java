package com.sgkrashi.cropdoctor.rag.service;

import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;

/** A knowledge base entry paired with its raw cosine similarity against a query — debug/transparency use only (see {@code KnowledgeBaseController}'s search endpoint); {@code retrieveForCrop} never exposes scores to callers that don't need them. */
public record ScoredKnowledgeBaseEntry(KnowledgeBaseEntry entry, double similarity) {
}
