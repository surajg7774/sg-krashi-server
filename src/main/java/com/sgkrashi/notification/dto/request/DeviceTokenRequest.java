package com.sgkrashi.notification.dto.request;

import com.sgkrashi.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRequest(
        @NotBlank(message = "Token is required")
        String token,

        @NotNull(message = "Platform is required")
        DevicePlatform platform
) {
}
