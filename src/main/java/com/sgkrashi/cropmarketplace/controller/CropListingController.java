package com.sgkrashi.cropmarketplace.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.request.CropListingFilterRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.cropmarketplace.service.CropListingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public, unauthenticated — Guests browse the crop marketplace freely, same as the product catalog. */
@RestController
@RequestMapping("/api/v1/crop-listings")
public class CropListingController {

    private final CropListingService cropListingService;

    public CropListingController(CropListingService cropListingService) {
        this.cropListingService = cropListingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CropListingSummaryResponse>>> list(
            @RequestParam(required = false) String cropType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) LocalDate harvestDateFrom,
            @RequestParam(required = false) LocalDate harvestDateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        CropListingFilterRequest filter = new CropListingFilterRequest(
                cropType, minPrice, maxPrice, harvestDateFrom, harvestDateTo, search, page, size);
        var result = cropListingService.listCropListings(filter);
        return ResponseEntity.ok(ApiResponse.success(result, "Crop listings retrieved"));
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<CropListingDetailResponse>> getOne(@PathVariable String idOrSlug) {
        CropListingDetailResponse response = cropListingService.getCropListingDetail(idOrSlug);
        return ResponseEntity.ok(ApiResponse.success(response, "Crop listing retrieved"));
    }
}
