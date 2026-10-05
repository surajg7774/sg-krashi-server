package com.sgkrashi.customer.dto.response;

import java.util.List;

public record CustomerProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        List<String> roles,
        // Additive (older clients ignore them): which re-authentication the
        // account-deletion dialog should ask for.
        boolean hasPassword,
        boolean googleLinked
) {
}
