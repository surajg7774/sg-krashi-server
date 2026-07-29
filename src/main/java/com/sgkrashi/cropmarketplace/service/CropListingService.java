package com.sgkrashi.cropmarketplace.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.request.CropListingFilterRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;

public interface CropListingService {

    PaginatedResponse<CropListingSummaryResponse> listCropListings(CropListingFilterRequest filter);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if no active listing matches */
    CropListingDetailResponse getCropListingDetail(String idOrSlug);
}
