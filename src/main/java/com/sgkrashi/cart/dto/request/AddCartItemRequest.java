package com.sgkrashi.cart.dto.request;

import com.sgkrashi.common.entity.ItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull(message = "Item type is required")
        ItemType itemType,

        @NotNull(message = "Item is required")
        Long itemId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {
}
