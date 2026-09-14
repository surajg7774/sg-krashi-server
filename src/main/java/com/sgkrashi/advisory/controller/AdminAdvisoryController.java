package com.sgkrashi.advisory.controller;

import com.sgkrashi.advisory.scheduler.WeatherAdvisoryJob;
import com.sgkrashi.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for {@code WeatherAdvisoryJob} — same convention as the other jobs' admin-run endpoints. */
@RestController
@RequestMapping("/api/v1/admin/weather-advisory")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminAdvisoryController {

    private final WeatherAdvisoryJob weatherAdvisoryJob;

    public AdminAdvisoryController(WeatherAdvisoryJob weatherAdvisoryJob) {
        this.weatherAdvisoryJob = weatherAdvisoryJob;
    }

    @PostMapping("/run-batch-job")
    public ResponseEntity<ApiResponse<Integer>> runBatchJob() {
        int notified = weatherAdvisoryJob.runOnce();
        return ResponseEntity.ok(ApiResponse.success(notified, notified + " farmer(s) notified"));
    }
}
