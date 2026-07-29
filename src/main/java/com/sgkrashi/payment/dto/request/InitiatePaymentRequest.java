package com.sgkrashi.payment.dto.request;

import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull(message = "Order is required") Long orderId
) {}
