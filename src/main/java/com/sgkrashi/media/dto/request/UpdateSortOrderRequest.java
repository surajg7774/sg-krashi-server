package com.sgkrashi.media.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSortOrderRequest(
        @NotNull(message = "sortOrder is required")
        @Min(value = 0, message = "sortOrder cannot be negative")
        Integer sortOrder
) {
}
