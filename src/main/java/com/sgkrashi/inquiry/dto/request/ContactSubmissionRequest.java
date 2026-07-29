package com.sgkrashi.inquiry.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactSubmissionRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        String phone,

        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String message
) {
}
