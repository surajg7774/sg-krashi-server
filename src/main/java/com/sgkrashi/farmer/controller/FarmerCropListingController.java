package com.sgkrashi.farmer.controller;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.farmer.dto.request.FarmerCropListingRequest;
import com.sgkrashi.farmer.service.FarmerCropListingService;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

/** Farmer-only write surface for a Farmer's own Crop Listings — see {@code FarmerCropListingService}'s Javadoc for the ownership-scoping approach. */
@RestController
@RequestMapping("/api/v1/farmer/crop-listings")
@PreAuthorize("hasRole('FARMER')")
public class FarmerCropListingController {

    private final FarmerCropListingService farmerCropListingService;
    private final CurrentUserProvider currentUserProvider;

    public FarmerCropListingController(FarmerCropListingService farmerCropListingService, CurrentUserProvider currentUserProvider) {
        this.farmerCropListingService = farmerCropListingService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CropListingSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                farmerCropListingService.listOwnListings(farmerId, search, page, size), "Crop listings retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CropListingDetailResponse>> getOne(@PathVariable Long id) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(farmerCropListingService.getOwnListing(farmerId, id), "Crop listing retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CropListingDetailResponse>> create(@Valid @RequestBody FarmerCropListingRequest request) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(farmerCropListingService.createOwnListing(farmerId, request), "Crop listing created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CropListingDetailResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody FarmerCropListingRequest request
    ) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(farmerCropListingService.updateOwnListing(farmerId, id, request), "Crop listing updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        farmerCropListingService.deactivateOwnListing(farmerId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Crop listing deactivated"));
    }

    /**
     * Farmer-scoped equivalent of {@code MediaController.upload} — same
     * validation and storage path, just ownership-checked against this
     * listing before anything is stored. A cross-farmer attempt (an id that
     * exists but isn't this farmer's) 404s the same way every other
     * ownership-scoped endpoint on this controller does.
     */
    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadMedia(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        MediaAssetResponse response = farmerCropListingService.uploadOwnListingMedia(farmerId, id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Photo uploaded"));
    }
}
