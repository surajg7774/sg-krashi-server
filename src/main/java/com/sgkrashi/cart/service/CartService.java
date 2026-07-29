package com.sgkrashi.cart.service;

import com.sgkrashi.cart.dto.request.AddCartItemRequest;
import com.sgkrashi.cart.dto.request.UpdateCartItemRequest;
import com.sgkrashi.cart.dto.response.CartResponse;

public interface CartService {

    /** Returns an empty cart (no row created) if the user has never added anything. */
    CartResponse getCart();

    /**
     * Adds a product to the authenticated user's cart, auto-creating the cart on
     * first use. Increments quantity if the product is already in the cart
     * rather than creating a duplicate row.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the product doesn't exist
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the requested quantity exceeds stock
     */
    CartResponse addItem(AddCartItemRequest request);

    /**
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the item doesn't exist or isn't the caller's
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the requested quantity exceeds stock
     */
    CartResponse updateItem(Long itemId, UpdateCartItemRequest request);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if the item doesn't exist or isn't the caller's */
    CartResponse removeItem(Long itemId);
}
