package com.sgkrashi.farmstay.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.farmstay.dto.response.StayListingDetailResponse;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;
import com.sgkrashi.farmstay.service.StayListingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated — same as the equipment catalog endpoints. */
@RestController
@RequestMapping("/api/v1/farm-stay")
public class StayListingController {

    private final StayListingService stayListingService;

    public StayListingController(StayListingService stayListingService) {
        this.stayListingService = stayListingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<StayListingSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(stayListingService.listStays(search, page, size), "Stay listings retrieved"));
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<StayListingDetailResponse>> getOne(@PathVariable String idOrSlug) {
        return ResponseEntity.ok(ApiResponse.success(stayListingService.getStayDetail(idOrSlug), "Stay listing retrieved"));
    }
}
