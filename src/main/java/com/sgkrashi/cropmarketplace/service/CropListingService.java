package com.sgkrashi.cropmarketplace.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.request.CropListingAdminRequest;
import com.sgkrashi.cropmarketplace.dto.request.CropListingFilterRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;

public interface CropListingService {

    PaginatedResponse<CropListingSummaryResponse> listCropListings(CropListingFilterRequest filter);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if no active listing matches */
    CropListingDetailResponse getCropListingDetail(String idOrSlug);

    /** Module 15 — Admin only. */
    CropListingDetailResponse createCropListing(CropListingAdminRequest request);

    /**
     * Module 20 - Farmer-owned creation. farmerId is always the caller's own
     * id, resolved server-side by FarmerCropListingService - never accepted
     * on CropListingAdminRequest, which structurally has no such field.
     */
    CropListingDetailResponse createCropListing(CropListingAdminRequest request, Long farmerId);

    /** Module 15 — Admin only. */
    CropListingDetailResponse updateCropListing(Long id, CropListingAdminRequest request);

    /** Module 15 — Admin only. Soft delete (is_active = false). */
    void deactivateCropListing(Long id);

    /** Module 15 — Admin only. Unlike {@link #listCropListings}, does NOT filter by isActive — see {@code ProductService.listProductsForAdmin}'s Javadoc for why. */
    PaginatedResponse<CropListingSummaryResponse> listCropListingsForAdmin(String search, int page, int size);

    /** Module 20 - same admin-style listing (search, includes inactive), additionally scoped to one farmer's own listings. */
    PaginatedResponse<CropListingSummaryResponse> listCropListingsForFarmer(String search, Long farmerId, int page, int size);

    /** Module 15 — Admin only. Unlike {@link #getCropListingDetail}, does NOT require the listing to be active. */
    CropListingDetailResponse getCropListingForAdmin(Long id);
}
