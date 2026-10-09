package com.sgkrashi.order.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code deliverySlotId} and {@code deliveryDate} are only read when the cart contains dairy; for any other cart they
 * are ignored, so older clients (which send only {@code addressId}) behave exactly as before.
 */
public record CheckoutRequest(
        @NotNull(message = "Shipping address is required") Long addressId,
        Long deliverySlotId,
        LocalDate deliveryDate
) {
    public CheckoutRequest(Long addressId) {
        this(addressId, null, null);
    }
}
