package com.sgkrashi.customer.dto.response;

import java.util.List;

public record CustomerProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        List<String> roles
) {
}
