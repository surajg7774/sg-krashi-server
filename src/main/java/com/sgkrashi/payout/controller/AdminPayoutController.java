package com.sgkrashi.payout.controller;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.payout.dto.response.AdminPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.AdminPayoutSummaryResponse;
import com.sgkrashi.payout.entity.PayoutStatus;
import com.sgkrashi.payout.scheduler.PayoutBatchJob;
import com.sgkrashi.payout.service.PayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform-wide Payout visibility and approval (Farmer Payout System) — every Admin/Super Admin sees every farmer's payouts. */
@RestController
@RequestMapping("/api/v1/admin/payouts")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminPayoutController {

    private final PayoutService payoutService;
    private final PayoutBatchJob payoutBatchJob;
    private final CurrentUserProvider currentUserProvider;

    public AdminPayoutController(PayoutService payoutService, PayoutBatchJob payoutBatchJob, CurrentUserProvider currentUserProvider) {
        this.payoutService = payoutService;
        this.payoutBatchJob = payoutBatchJob;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminPayoutSummaryResponse>>> list(
            @RequestParam(required = false) PayoutStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.listForAdmin(status, page, size), "Payouts retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminPayoutDetailResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.getDetailForAdmin(id), "Payout retrieved"));
    }

    /** BATCHED -> APPROVED. Money moves outside the app only after this — no payout gateway is integrated in this pass. */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminPayoutDetailResponse>> approve(@PathVariable Long id) {
        Long adminId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(payoutService.approve(id, adminId), "Payout approved"));
    }

    /** APPROVED -> PAID, asserted by the Admin after manually transferring funds outside the app. */
    @PatchMapping("/{id}/mark-paid")
    public ResponseEntity<ApiResponse<AdminPayoutDetailResponse>> markPaid(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.markPaid(id), "Payout marked as paid"));
    }

    /**
     * Manually runs {@code PayoutBatchJob} on demand — lets an Admin (or
     * this platform's own test/verification tooling) confirm the weekly
     * batch works without waiting for its real schedule. Idempotent, same
     * as the scheduled run itself.
     */
    @PostMapping("/run-batch-job")
    public ResponseEntity<ApiResponse<Integer>> runBatchJob() {
        int farmersProcessed = payoutBatchJob.runOnce();
        return ResponseEntity.ok(ApiResponse.success(farmersProcessed, farmersProcessed + " farmer(s) had payouts swept"));
    }
}
