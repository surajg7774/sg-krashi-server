package com.sgkrashi.customer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must be at most 255 characters")
        String line1,

        @Size(max = 255, message = "Address line 2 must be at most 255 characters")
        String line2,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must be at most 100 characters")
        String state,

        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^\\d{6}$", message = "Pincode must be exactly 6 digits")
        String pincode
) {
}
