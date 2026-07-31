package com.sgkrashi.cms.controller;

import com.sgkrashi.cms.dto.response.ContentBlockResponse;
import com.sgkrashi.cms.entity.ContentBlockType;
import com.sgkrashi.cms.service.ContentBlockService;
import com.sgkrashi.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated — consumed by Module 3's public pages (homepage hero, testimonials). */
@RestController
@RequestMapping("/api/v1/cms/content-blocks")
public class ContentBlockController {

    private final ContentBlockService contentBlockService;

    public ContentBlockController(ContentBlockService contentBlockService) {
        this.contentBlockService = contentBlockService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContentBlockResponse>>> list(
            @RequestParam(required = false) ContentBlockType type
    ) {
        return ResponseEntity.ok(ApiResponse.success(contentBlockService.listPublic(type), "Content blocks retrieved"));
    }
}
