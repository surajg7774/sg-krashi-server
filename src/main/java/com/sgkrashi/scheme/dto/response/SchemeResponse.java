package com.sgkrashi.scheme.dto.response;

import com.sgkrashi.scheme.entity.SchemeCategory;

public record SchemeResponse(
        Long id,
        String name,
        SchemeCategory category,
        String description,
        String eligibility,
        String benefit,
        String officialLink,
        String stateScope,
        int sortOrder,
        boolean isActive
) {
}
