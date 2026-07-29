package com.sgkrashi.order.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.order.dto.request.CheckoutRequest;
import com.sgkrashi.order.dto.response.OrderResponse;
import com.sgkrashi.order.dto.response.OrderSummaryResponse;
import com.sgkrashi.order.entity.Order;

public interface OrderService {

    /**
     * Converts the authenticated user's cart into an order: validates stock for
     * every line under a pessimistic lock, snapshots prices and the shipping
     * address, decrements stock, clears the cart, and records the initial
     * PENDING_PAYMENT status. Fully atomic — any validation failure rolls back
     * every change.
     *
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the cart is empty or any line is now out of stock
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the address doesn't exist or isn't the caller's
     */
    OrderResponse checkout(CheckoutRequest request);

    PaginatedResponse<OrderSummaryResponse> listMyOrders(int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if the order doesn't exist or isn't the caller's */
    OrderResponse getOrderDetail(Long orderId);

    /** Transitions an order to CONFIRMED once its payment has settled. Idempotent no-op if already CONFIRMED. */
    void markConfirmed(Long orderId);

    /** Transitions an order to PAYMENT_FAILED and restores the stock reserved at checkout. */
    void markPaymentFailed(Long orderId);

    /** Internal lookup used by the payment module — not exposed over HTTP. */
    Order getOrderEntityOrThrow(Long orderId);
}
