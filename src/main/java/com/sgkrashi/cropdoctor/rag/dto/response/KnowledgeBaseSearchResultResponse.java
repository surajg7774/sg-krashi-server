package com.sgkrashi.cropdoctor.rag.dto.response;

/** One semantic-search result — {@code similarity} is the raw cosine similarity score, exposed for transparency (see {@code KnowledgeBaseController#search}). */
public record KnowledgeBaseSearchResultResponse(KnowledgeBaseEntryResponse entry, double similarity) {
}
