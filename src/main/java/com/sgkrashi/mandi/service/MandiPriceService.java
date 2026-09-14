package com.sgkrashi.mandi.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.mandi.dto.response.MandiFilterOptionsResponse;
import com.sgkrashi.mandi.dto.response.MandiPriceResponse;
import com.sgkrashi.mandi.dto.response.MandiSyncMetaResponse;
import com.sgkrashi.mandi.dto.response.MandiTrendPointResponse;

import java.util.List;

public interface MandiPriceService {

    PaginatedResponse<MandiPriceResponse> search(String commodity, String state, String market, int page, int size);

    MandiFilterOptionsResponse getFilterOptions(String state);

    List<MandiTrendPointResponse> getTrend(String commodity, String state, String market);

    MandiSyncMetaResponse getSyncMeta();
}
