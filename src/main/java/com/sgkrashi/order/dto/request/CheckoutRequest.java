package com.sgkrashi.order.dto.request;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull(message = "Shipping address is required") Long addressId
) {}
