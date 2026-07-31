package com.sgkrashi.farmer.service;

import com.sgkrashi.farmer.dto.response.FarmerDashboardSummaryResponse;

public interface FarmerDashboardService {

    FarmerDashboardSummaryResponse getSummary(Long farmerId);
}
