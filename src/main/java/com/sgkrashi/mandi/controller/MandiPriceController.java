package com.sgkrashi.mandi.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.mandi.dto.response.MandiFilterOptionsResponse;
import com.sgkrashi.mandi.dto.response.MandiPriceResponse;
import com.sgkrashi.mandi.dto.response.MandiSyncMetaResponse;
import com.sgkrashi.mandi.dto.response.MandiTrendPointResponse;
import com.sgkrashi.mandi.service.MandiPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated — read-only reference data, sourced from Agmarknet/Government of India, not real-time. */
@RestController
@RequestMapping("/api/v1/mandi")
public class MandiPriceController {

    private final MandiPriceService mandiPriceService;

    public MandiPriceController(MandiPriceService mandiPriceService) {
        this.mandiPriceService = mandiPriceService;
    }

    @GetMapping("/prices")
    public ResponseEntity<ApiResponse<PaginatedResponse<MandiPriceResponse>>> search(
            @RequestParam(required = false) String commodity,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String market,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(mandiPriceService.search(commodity, state, market, page, size), "Mandi prices retrieved"));
    }

    @GetMapping("/prices/filters")
    public ResponseEntity<ApiResponse<MandiFilterOptionsResponse>> filters(@RequestParam(required = false) String state) {
        return ResponseEntity.ok(ApiResponse.success(mandiPriceService.getFilterOptions(state), "Filter options retrieved"));
    }

    @GetMapping("/prices/trend")
    public ResponseEntity<ApiResponse<List<MandiTrendPointResponse>>> trend(
            @RequestParam String commodity,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String market
    ) {
        return ResponseEntity.ok(ApiResponse.success(mandiPriceService.getTrend(commodity, state, market), "Price trend retrieved"));
    }

    @GetMapping("/prices/meta")
    public ResponseEntity<ApiResponse<MandiSyncMetaResponse>> meta() {
        return ResponseEntity.ok(ApiResponse.success(mandiPriceService.getSyncMeta(), "Sync metadata retrieved"));
    }
}
