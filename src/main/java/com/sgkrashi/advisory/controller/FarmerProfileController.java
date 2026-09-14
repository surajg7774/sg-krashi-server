package com.sgkrashi.advisory.controller;

import com.sgkrashi.advisory.dto.FarmerProfileRequest;
import com.sgkrashi.advisory.dto.FarmerProfileResponse;
import com.sgkrashi.advisory.service.FarmerProfileService;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A Farmer's own location + weather-advisory opt-in — same ownership-scoped shape as {@code FarmerPayoutController}. */
@RestController
@RequestMapping("/api/v1/farmer/profile")
@PreAuthorize("hasRole('FARMER')")
public class FarmerProfileController {

    private final FarmerProfileService farmerProfileService;
    private final CurrentUserProvider currentUserProvider;

    public FarmerProfileController(FarmerProfileService farmerProfileService, CurrentUserProvider currentUserProvider) {
        this.farmerProfileService = farmerProfileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FarmerProfileResponse>> getOwn() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(farmerProfileService.getOwnProfile(userId), "Farmer profile retrieved"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<FarmerProfileResponse>> updateOwn(@Valid @RequestBody FarmerProfileRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(farmerProfileService.updateOwnProfile(userId, request), "Farmer profile updated"));
    }
}
