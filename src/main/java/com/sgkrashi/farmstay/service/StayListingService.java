package com.sgkrashi.farmstay.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.farmstay.dto.response.StayListingDetailResponse;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;

public interface StayListingService {

    PaginatedResponse<StayListingSummaryResponse> listStays(int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if no active listing matches */
    StayListingDetailResponse getStayDetail(String idOrSlug);
}
