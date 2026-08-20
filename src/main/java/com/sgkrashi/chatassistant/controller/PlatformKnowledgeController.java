package com.sgkrashi.chatassistant.controller;

import com.sgkrashi.chatassistant.dto.response.PlatformKnowledgeSearchResultResponse;
import com.sgkrashi.chatassistant.knowledge.service.PlatformKnowledgeService;
import com.sgkrashi.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Debug/transparency endpoint for the chat assistant's platform knowledge
 * base — same reasoning as {@code com.sgkrashi.cropdoctor.rag.controller.KnowledgeBaseController#search}:
 * lets retrieval quality actually be inspected (real similarity scores),
 * rather than trusted blind. Public for the same reason too: reference
 * documentation content only, no user data.
 */
@RestController
@RequestMapping("/api/v1/chat/knowledge-base")
public class PlatformKnowledgeController {

    private static final int DEBUG_SEARCH_RESULTS = 26;

    private final PlatformKnowledgeService platformKnowledgeService;

    public PlatformKnowledgeController(PlatformKnowledgeService platformKnowledgeService) {
        this.platformKnowledgeService = platformKnowledgeService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PlatformKnowledgeSearchResultResponse>>> search(@RequestParam String query) {
        List<PlatformKnowledgeSearchResultResponse> results = platformKnowledgeService
                .searchWithScores(query, DEBUG_SEARCH_RESULTS).stream()
                .map(scored -> new PlatformKnowledgeSearchResultResponse(
                        scored.entry().getId(), scored.entry().getCategory(), scored.entry().getTitle(),
                        scored.entry().getContent(), scored.similarity()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(results, "Platform knowledge search results retrieved"));
    }
}
