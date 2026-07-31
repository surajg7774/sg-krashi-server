package com.sgkrashi.order.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.order.dto.request.CheckoutRequest;
import com.sgkrashi.order.dto.response.AdminOrderDetailResponse;
import com.sgkrashi.order.dto.response.AdminOrderSummaryResponse;
import com.sgkrashi.order.dto.response.OrderResponse;
import com.sgkrashi.order.dto.response.OrderSummaryResponse;
import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderStatus;

import java.time.Instant;

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

    /**
     * Transitions an order to REFUNDED once {@code RefundService} has already
     * processed the real gateway refund. Idempotent no-op if already REFUNDED —
     * never called except from {@code RefundService}, which itself guards
     * against calling it twice for the same refund.
     */
    void markRefunded(Long orderId);

    /** Internal lookup used by the payment module — not exposed over HTTP. */
    Order getOrderEntityOrThrow(Long orderId);

    /** Admin-wide, unfiltered-by-owner listing (Module 16) — every order platform-wide, not just the caller's own. */
    PaginatedResponse<AdminOrderSummaryResponse> listOrdersForAdmin(
            OrderStatus status, Long userId, Instant dateFrom, Instant dateTo, int page, int size);

    /** Admin detail view — unlike {@link #getOrderDetail}, not scoped to the caller's own orders. */
    AdminOrderDetailResponse getOrderDetailForAdmin(Long orderId);

    /**
     * Admin-driven status change. {@code newStatus} must be CONFIRMED or
     * PAYMENT_FAILED — REFUNDED is reachable only via {@code RefundService}'s
     * real refund flow, never through this plain status-update endpoint.
     * {@code adminNotes} is always applied, independent of whether the status
     * actually changes (a no-op re-submission of the current status is allowed
     * purely to update notes).
     *
     * @throws com.sgkrashi.common.exception.BusinessRuleException if {@code newStatus} is REFUNDED, or isn't a real reachable status for this action
     */
    AdminOrderDetailResponse updateOrderStatus(Long orderId, OrderStatus newStatus, String adminNotes);
}
