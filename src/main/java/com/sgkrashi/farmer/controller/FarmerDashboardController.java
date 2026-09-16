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

/**
 * Note for anyone testing this by hand: the real endpoint is {@code GET
 * /api/v1/farmer/dashboard/summary} — the bare {@code /api/v1/farmer/dashboard}
 * path (no sub-path) has no handler at all and 404s for every caller
 * regardless of role, it's not where the {@code hasRole('FARMER')} check
 * below actually runs. Worth calling out explicitly: hitting the bare path
 * was mistaken for a role-authorization bug once already (it 500'd before
 * GlobalExceptionHandler got a NoResourceFoundException handler — see that
 * class — which made the real "wrong role" case at /summary, a clean 403,
 * easy to miss).
 */
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
