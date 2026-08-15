package com.sgkrashi.cropdoctor.rag.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.cropdoctor.rag.dto.response.KnowledgeBaseEntryResponse;
import com.sgkrashi.cropdoctor.rag.entity.KnowledgeBaseEntry;
import com.sgkrashi.cropdoctor.rag.service.KnowledgeBaseIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Transparency/debugging endpoint (task spec section 4.4) — lets anyone
 * inspect exactly what the knowledge base contains and how the crop filter
 * would resolve for a given value, same content {@code RetrievalService}
 * draws from for actual scans. Public, not admin-only: this is reference
 * content with no user data in it (unlike, say, {@code AdminUserController}),
 * so there's nothing here that needs gating — same reasoning as the public
 * {@code /supported-crops} endpoint right next to it.
 */
@RestController
@RequestMapping("/api/v1/ai/crop-doctor/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseIngestionService knowledgeBaseIngestionService;

    public KnowledgeBaseController(KnowledgeBaseIngestionService knowledgeBaseIngestionService) {
        this.knowledgeBaseIngestionService = knowledgeBaseIngestionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<KnowledgeBaseEntryResponse>>> list(
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String topic
    ) {
        List<KnowledgeBaseEntryResponse> entries = knowledgeBaseIngestionService.list(crop, topic).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(entries, "Knowledge base entries retrieved"));
    }

    private KnowledgeBaseEntryResponse toResponse(KnowledgeBaseEntry entry) {
        return new KnowledgeBaseEntryResponse(
                entry.getId(), entry.getCrop(), entry.getTopic(), entry.getTitle(), entry.getContent(), entry.getSource());
    }
}
