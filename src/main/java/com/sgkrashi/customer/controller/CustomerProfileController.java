package com.sgkrashi.customer.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.customer.dto.request.ChangePasswordRequest;
import com.sgkrashi.customer.dto.request.UpdateProfileRequest;
import com.sgkrashi.customer.dto.response.CustomerProfileResponse;
import com.sgkrashi.customer.service.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's own profile. There is deliberately no
 * {@code GET /api/v1/customers/{id}} — only {@code /me} — so there is no ID
 * for a caller to manipulate in the first place.
 */
@RestController
@RequestMapping("/api/v1/customers/me")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success(customerProfileService.getProfile(), "Profile retrieved"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerProfileService.updateProfile(request), "Profile updated"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        customerProfileService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}
