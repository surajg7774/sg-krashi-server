package com.sgkrashi.admin.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminUserDetailResponse(
        Long id,
        String name,
        String email,
        String phone,
        List<String> roles,
        boolean isActive,
        Instant createdAt,
        long orderCount,
        long bookingCount,
        long inquiryCount,
        long reviewCount
) {
}
