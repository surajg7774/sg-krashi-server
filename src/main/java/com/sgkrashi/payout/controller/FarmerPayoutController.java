package com.sgkrashi.payout.controller;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutSummaryResponse;
import com.sgkrashi.payout.dto.response.PendingPayoutSummaryResponse;
import com.sgkrashi.payout.service.PayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A Farmer's own payout history — ownership-scoped the same way {@code FarmerCropListingController} is (404, never 403, on a payout that isn't this farmer's). */
@RestController
@RequestMapping("/api/v1/farmer/payouts")
@PreAuthorize("hasRole('FARMER')")
public class FarmerPayoutController {

    private final PayoutService payoutService;
    private final CurrentUserProvider currentUserProvider;

    public FarmerPayoutController(PayoutService payoutService, CurrentUserProvider currentUserProvider) {
        this.payoutService = payoutService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<FarmerPayoutSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(payoutService.listOwnPayouts(farmerId, page, size), "Payouts retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FarmerPayoutDetailResponse>> getOne(@PathVariable Long id) {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(payoutService.getOwnPayoutDetail(farmerId, id), "Payout retrieved"));
    }

    /** Live "pending (accrued, not yet batched)" figure — see {@code PendingPayoutSummaryResponse}'s Javadoc. */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PendingPayoutSummaryResponse>> getPending() {
        Long farmerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(payoutService.getPendingAccrued(farmerId), "Pending payout summary retrieved"));
    }
}
