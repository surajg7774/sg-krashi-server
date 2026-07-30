package com.sgkrashi.cropmarketplace.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.request.CropListingAdminRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.cropmarketplace.service.CropListingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only write surface for Crop Listings — see {@code AdminProductController}'s Javadoc for why this is a sibling in the domain package, not under {@code com.sgkrashi.admin}. */
@RestController
@RequestMapping("/api/v1/admin/crop-listings")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminCropListingController {

    private final CropListingService cropListingService;

    public AdminCropListingController(CropListingService cropListingService) {
        this.cropListingService = cropListingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CropListingSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(cropListingService.listCropListingsForAdmin(search, page, size), "Crop listings retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CropListingDetailResponse>> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropListingService.getCropListingForAdmin(id), "Crop listing retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CropListingDetailResponse>> create(@Valid @RequestBody CropListingAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cropListingService.createCropListing(request), "Crop listing created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CropListingDetailResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CropListingAdminRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(cropListingService.updateCropListing(id, request), "Crop listing updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        cropListingService.deactivateCropListing(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Crop listing deactivated"));
    }
}
