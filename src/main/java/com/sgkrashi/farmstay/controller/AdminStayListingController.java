package com.sgkrashi.farmstay.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.farmstay.dto.request.StayListingAdminRequest;
import com.sgkrashi.farmstay.dto.response.StayListingDetailResponse;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;
import com.sgkrashi.farmstay.service.StayListingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only write surface for Stay Listings — see {@code AdminProductController}'s Javadoc for why this is a sibling in the domain package. */
@RestController
@RequestMapping("/api/v1/admin/stay-listings")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminStayListingController {

    private final StayListingService stayListingService;

    public AdminStayListingController(StayListingService stayListingService) {
        this.stayListingService = stayListingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<StayListingSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(stayListingService.listStaysForAdmin(search, page, size), "Stay listings retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StayListingDetailResponse>> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(stayListingService.getStayForAdmin(id), "Stay listing retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StayListingDetailResponse>> create(@Valid @RequestBody StayListingAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(stayListingService.createStayListing(request), "Stay listing created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StayListingDetailResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody StayListingAdminRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stayListingService.updateStayListing(id, request), "Stay listing updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        stayListingService.deactivateStayListing(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Stay listing deactivated"));
    }
}
