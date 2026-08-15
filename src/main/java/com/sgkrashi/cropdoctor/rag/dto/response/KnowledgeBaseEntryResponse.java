package com.sgkrashi.cropdoctor.rag.dto.response;

public record KnowledgeBaseEntryResponse(
        Long id,
        String crop,
        String topic,
        String title,
        String content,
        String source
) {
}
