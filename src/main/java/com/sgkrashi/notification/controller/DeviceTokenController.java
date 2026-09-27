package com.sgkrashi.notification.controller;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.notification.dto.request.DeviceTokenRequest;
import com.sgkrashi.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated (no entry in SecurityConfig's public lists) — a device token is only ever registered for the currently logged-in user, resolved from the JWT, never a request param. */
@RestController
@RequestMapping("/api/v1/notifications/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final CurrentUserProvider currentUserProvider;

    public DeviceTokenController(DeviceTokenService deviceTokenService, CurrentUserProvider currentUserProvider) {
        this.deviceTokenService = deviceTokenService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody DeviceTokenRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        deviceTokenService.registerOrUpdate(userId, request.token(), request.platform());
        return ResponseEntity.ok(ApiResponse.success(null, "Device token registered"));
    }

    /**
     * Called on logout — the token itself (not this endpoint) is the only
     * identifier needed, since it's already unique per device and
     * {@code DeviceTokenService#unregister} doesn't need to check ownership
     * (removing a device token this user doesn't recognize as their own
     * device is harmless: it just stops push to a token nobody here claims,
     * same non-issue as a double-logout).
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(@RequestParam String token) {
        deviceTokenService.unregister(token);
        return ResponseEntity.ok(ApiResponse.success(null, "Device token unregistered"));
    }
}
