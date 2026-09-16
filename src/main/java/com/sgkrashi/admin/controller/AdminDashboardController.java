package com.sgkrashi.admin.controller;

import com.sgkrashi.admin.dto.response.DashboardSummaryResponse;
import com.sgkrashi.admin.service.AdminDashboardService;
import com.sgkrashi.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Note for anyone testing this by hand: the real endpoint is {@code GET
 * /api/v1/admin/dashboard/summary} — the bare {@code /api/v1/admin/dashboard}
 * path (no sub-path) has no handler at all and 404s for every caller
 * regardless of role, it's not where the {@code hasAnyRole(...)} check below
 * actually runs. Worth calling out explicitly: hitting the bare path was
 * mistaken for a role-authorization bug once already (it 500'd before
 * GlobalExceptionHandler got a NoResourceFoundException handler — see that
 * class — which made the real "wrong role" case at /summary, a clean 403,
 * easy to miss).
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getSummary(), "Dashboard summary retrieved"));
    }
}
