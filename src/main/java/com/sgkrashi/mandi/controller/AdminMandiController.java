package com.sgkrashi.mandi.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.mandi.scheduler.MandiPriceSyncJob;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for {@code MandiPriceSyncJob} — same convention as the booking/payout jobs' admin-run endpoints. */
@RestController
@RequestMapping("/api/v1/admin/mandi")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminMandiController {

    private final MandiPriceSyncJob mandiPriceSyncJob;

    public AdminMandiController(MandiPriceSyncJob mandiPriceSyncJob) {
        this.mandiPriceSyncJob = mandiPriceSyncJob;
    }

    @PostMapping("/run-sync-job")
    public ResponseEntity<ApiResponse<Integer>> runSyncJob() {
        int synced = mandiPriceSyncJob.runOnce();
        return ResponseEntity.ok(ApiResponse.success(synced, synced + " mandi price row(s) synced"));
    }
}
