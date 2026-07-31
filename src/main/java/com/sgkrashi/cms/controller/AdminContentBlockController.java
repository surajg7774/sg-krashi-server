package com.sgkrashi.cms.controller;

import com.sgkrashi.cms.dto.request.ContentBlockRequest;
import com.sgkrashi.cms.dto.response.ContentBlockResponse;
import com.sgkrashi.cms.service.ContentBlockService;
import com.sgkrashi.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CMS editing is deliberately open to plain ADMIN, not restricted to SUPER_ADMIN
 * like {@code AdminAuditLogController} — editing homepage copy is ordinary
 * day-to-day content work, not the kind of sensitive/accountability-adjacent
 * capability the architecture doc reserves for Super Admin specifically.
 */
@RestController
@RequestMapping("/api/v1/admin/content-blocks")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminContentBlockController {

    private final ContentBlockService contentBlockService;

    public AdminContentBlockController(ContentBlockService contentBlockService) {
        this.contentBlockService = contentBlockService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContentBlockResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(contentBlockService.listForAdmin(), "Content blocks retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContentBlockResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(contentBlockService.getForAdmin(id), "Content block retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ContentBlockResponse>> create(@Valid @RequestBody ContentBlockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contentBlockService.create(request), "Content block created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ContentBlockResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ContentBlockRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(contentBlockService.update(id, request), "Content block updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        contentBlockService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Content block deactivated"));
    }
}
