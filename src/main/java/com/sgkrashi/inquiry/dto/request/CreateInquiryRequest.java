package com.sgkrashi.inquiry.dto.request;

import com.sgkrashi.inquiry.entity.InquiryModuleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Generalized from Module 3's {@code ContactSubmissionRequest} — same
 * required name/email/message shape, plus {@link #moduleType} (required, so
 * the caller always states what the inquiry is about) and the optional
 * {@link #preferredDate}/{@link #groupSize} fields visit-style inquiries
 * (e.g. Organic Farming) need but a general contact message doesn't.
 */
public record CreateInquiryRequest(
        @NotNull(message = "Module type is required")
        InquiryModuleType moduleType,

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        String phone,

        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String message,

        @FutureOrPresent(message = "Preferred date must not be in the past")
        LocalDate preferredDate,

        @Min(value = 1, message = "Group size must be at least 1")
        Integer groupSize
) {
}
