package com.sgkrashi.farmer.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.farmer.dto.request.FarmerCropListingRequest;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ownership-scoping layer on top of {@code CropListingService} (Module 7/15)
 * — every method here narrows to one farmer's own listings, either by
 * filtering a query or by verifying ownership before delegating a mutation.
 * The actual create/update/query logic lives in {@code CropListingService};
 * this interface never re-implements it.
 */
public interface FarmerCropListingService {

    PaginatedResponse<CropListingSummaryResponse> listOwnListings(Long farmerId, String search, int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if the listing doesn't exist OR belongs to a different farmer — never distinguishes the two to the caller. */
    CropListingDetailResponse getOwnListing(Long farmerId, Long id);

    CropListingDetailResponse createOwnListing(Long farmerId, FarmerCropListingRequest request);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException on ownership mismatch — see {@link #getOwnListing}. */
    CropListingDetailResponse updateOwnListing(Long farmerId, Long id, FarmerCropListingRequest request);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException on ownership mismatch — see {@link #getOwnListing}. */
    void deactivateOwnListing(Long farmerId, Long id);

    /**
     * Uploads a photo for one of the Farmer's own crop listings. Reuses
     * {@code MediaService}'s existing validation (size limit, declared
     * Content-Type, and magic-byte detection) and whichever {@code
     * StorageProvider} is active verbatim — no new upload/validation logic.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException on ownership mismatch — see {@link #getOwnListing}
     * @throws com.sgkrashi.common.exception.ValidationException if the file fails {@code MediaService}'s validation
     */
    MediaAssetResponse uploadOwnListingMedia(Long farmerId, Long id, MultipartFile file);
}
