package com.sgkrashi.farmer.controller;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.farmer.dto.response.FarmerDashboardSummaryResponse;
import com.sgkrashi.farmer.service.FarmerDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/farmer/dashboard")
@PreAuthorize("hasRole('FARMER')")
public class FarmerDashboardController {

    private final FarmerDashboardService farmerDashboardService;
    private final CurrentUserProvider currentUserProvider;

    public FarmerDashboardController(FarmerDashboardService farmerDashboardService, CurrentUserProvider currentUserProvider) {
        this.farmerDashboardService = farmerDashboardService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<FarmerDashboardSummaryResponse>> getSummary() {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(farmerDashboardService.getSummary(farmerId), "Dashboard summary retrieved"));
    }
}
