package com.sgkrashi.farmstay.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Same create/update (PUT) shape as {@code ProductAdminRequest} — see its Javadoc. {@code amenities} is a list here; the entity stores it as a comma-separated string (Module 9's own choice, "no separate table for a small, fixed-shape list"). */
public record StayListingAdminRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        String slug,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Max guests is required")
        @Min(value = 1, message = "Max guests must be at least 1")
        Integer maxGuests,

        @NotNull(message = "Nightly rate is required")
        @DecimalMin(value = "0.01", message = "Nightly rate must be greater than zero")
        BigDecimal nightlyRate,

        List<String> amenities,

        @NotBlank(message = "Address line 1 is required")
        String addressLine1,

        String addressLine2,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "State is required")
        String state,

        @NotBlank(message = "Pincode is required")
        String pincode,

        boolean isAvailable,

        boolean isActive
) {
}
