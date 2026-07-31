package com.sgkrashi.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Module 20 — generic role grant/revoke, not FARMER-specific, so any future role gains the same admin-assignable path for free. */
public record UpdateUserRolesRequest(
        @NotBlank(message = "roleName is required")
        String roleName,

        @NotNull(message = "assign is required")
        Boolean assign
) {
}
