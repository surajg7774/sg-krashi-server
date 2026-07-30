package com.sgkrashi.productstore.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Used for both create and update (PUT semantics — full replace), same
 * pattern applied consistently across all four Module 15 catalog entities.
 * {@code slug} is optional — left blank, the service generates one from
 * {@code name} (see {@code SlugUtil}).
 */
public record ProductAdminRequest(
        @NotNull(message = "Category is required")
        Long categoryId,

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        String slug,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity cannot be negative")
        Integer stockQty,

        boolean isOrganicCertified,

        boolean isActive
) {
}
