package com.sgkrashi.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code payableType} is a plain validated string ("ORDER" or "BOOKING")
 * rather than a shared Java enum — Order and Booking are owned by separate
 * modules with no reason to share a type definition just for this one field.
 */
public record InitiatePaymentRequest(
        @NotBlank(message = "Payable type is required")
        @Pattern(regexp = "ORDER|BOOKING", message = "Payable type must be ORDER or BOOKING")
        String payableType,

        @NotNull(message = "Payable ID is required") Long payableId
) {}
