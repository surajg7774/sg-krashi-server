package com.sgkrashi.chatassistant.dto.response;

/** One semantic-search result against the platform knowledge base, with its raw similarity score — debug/transparency use only. */
public record PlatformKnowledgeSearchResultResponse(Long id, String category, String title, String content, double similarity) {
}
