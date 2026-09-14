package com.sgkrashi.scheme.dto.request;

import com.sgkrashi.scheme.entity.SchemeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SchemeRequest(
        @NotBlank String name,
        @NotNull SchemeCategory category,
        @NotBlank String description,
        @NotBlank String eligibility,
        @NotBlank String benefit,
        @NotBlank String officialLink,
        String stateScope,
        int sortOrder,
        boolean isActive
) {
}
