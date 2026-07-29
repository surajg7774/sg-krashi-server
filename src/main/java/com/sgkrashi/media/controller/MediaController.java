package com.sgkrashi.media.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.service.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generic image upload, associated with any owner entity via owner_type/owner_id.
 * No UI consumes this yet — Module 15's Admin product management is the first
 * real caller. Requires authentication (not on the public allow-list) since it's
 * a write endpoint; role-gating to Admin specifically arrives with Module 14/15's
 * role management.
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaAssetResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerType") String ownerType,
            @RequestParam(value = "ownerId", required = false) Long ownerId
    ) {
        MediaAssetResponse response = mediaService.upload(file, ownerType, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "File uploaded"));
    }
}
