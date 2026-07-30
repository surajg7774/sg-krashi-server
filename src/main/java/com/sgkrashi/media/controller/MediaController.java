package com.sgkrashi.media.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.service.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generic image upload, associated with any owner entity via owner_type/owner_id.
 * Module 15's Admin catalog management is the first real caller — now gated
 * to Admin specifically (previously just "any authenticated user," a real gap:
 * any logged-in Customer could have called this and attached arbitrary images
 * to arbitrary ownerId values, since there was no real caller yet to define
 * who "should" be allowed).
 */
@RestController
@RequestMapping("/api/v1/media")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
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
