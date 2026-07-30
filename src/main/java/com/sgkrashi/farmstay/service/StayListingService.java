package com.sgkrashi.farmstay.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.farmstay.dto.request.StayListingAdminRequest;
import com.sgkrashi.farmstay.dto.response.StayListingDetailResponse;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;

public interface StayListingService {

    PaginatedResponse<StayListingSummaryResponse> listStays(int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if no active listing matches */
    StayListingDetailResponse getStayDetail(String idOrSlug);

    /** Module 15 — Admin only. */
    StayListingDetailResponse createStayListing(StayListingAdminRequest request);

    /** Module 15 — Admin only. */
    StayListingDetailResponse updateStayListing(Long id, StayListingAdminRequest request);

    /** Module 15 — Admin only. Soft delete (is_active = false). */
    void deactivateStayListing(Long id);

    /** Module 15 — Admin only. Unlike {@link #listStays}, does NOT filter by isActive — see {@code ProductService.listProductsForAdmin}'s Javadoc for why. */
    PaginatedResponse<StayListingSummaryResponse> listStaysForAdmin(String search, int page, int size);

    /** Module 15 — Admin only. Unlike {@link #getStayDetail}, does NOT require the listing to be active. */
    StayListingDetailResponse getStayForAdmin(Long id);
}
