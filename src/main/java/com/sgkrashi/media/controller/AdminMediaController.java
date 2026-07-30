package com.sgkrashi.media.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.media.dto.request.UpdateSortOrderRequest;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only media management (delete, reorder) — a sibling of the existing
 * {@code MediaController} (upload only). Listing an entity's current media
 * needs no new endpoint: every catalog entity's own detail response already
 * embeds its {@code media} array (Module 5's original design), which is all
 * the Admin edit form needs to show existing images before adding/removing.
 */
@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminMediaController {

    private final MediaService mediaService;

    public AdminMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Media deleted"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> updateSortOrder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSortOrderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.updateSortOrder(id, request.sortOrder()), "Sort order updated"));
    }
}
