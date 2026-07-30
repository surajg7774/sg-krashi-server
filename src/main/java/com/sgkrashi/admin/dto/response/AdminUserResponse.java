package com.sgkrashi.admin.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String phone,
        List<String> roles,
        boolean isActive,
        Instant createdAt
) {
}
