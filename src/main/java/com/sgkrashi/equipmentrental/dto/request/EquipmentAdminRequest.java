package com.sgkrashi.equipmentrental.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Same create/update (PUT) shape as {@code ProductAdminRequest} — see its Javadoc. Category is a plain string here (Module 8's own "flat catalog" choice), not a FK. */
public record EquipmentAdminRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        String slug,

        @NotBlank(message = "Category is required")
        @Size(max = 100, message = "Category must be at most 100 characters")
        String category,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Daily rate is required")
        @DecimalMin(value = "0.01", message = "Daily rate must be greater than zero")
        BigDecimal dailyRate,

        boolean isAvailable,

        boolean isActive
) {
}
