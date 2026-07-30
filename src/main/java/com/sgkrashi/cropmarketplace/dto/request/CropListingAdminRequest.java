package com.sgkrashi.cropmarketplace.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Same create/update (PUT) shape as {@code ProductAdminRequest} — see its Javadoc. */
public record CropListingAdminRequest(
        @NotNull(message = "Category is required")
        Long categoryId,

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        String slug,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Quantity available is required")
        @Min(value = 0, message = "Quantity available cannot be negative")
        Integer quantityAvailable,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be greater than zero")
        BigDecimal unitPrice,

        @NotNull(message = "Harvest date is required")
        LocalDate harvestDate,

        boolean isActive
) {
}
