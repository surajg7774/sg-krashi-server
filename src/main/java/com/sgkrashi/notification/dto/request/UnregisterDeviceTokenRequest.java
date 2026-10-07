package com.sgkrashi.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UnregisterDeviceTokenRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
