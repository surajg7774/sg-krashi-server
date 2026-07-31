package com.sgkrashi.farmer.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.farmer.dto.request.FarmerCropListingRequest;

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
}
