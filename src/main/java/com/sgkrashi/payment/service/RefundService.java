package com.sgkrashi.payment.service;

import com.sgkrashi.payment.dto.response.RefundResultResponse;

public interface RefundService {

    /**
     * Refunds the PAID payment behind an Order in full, via Razorpay's real
     * refund API, then marks the Order REFUNDED and notifies the customer.
     *
     * <p><b>Idempotent:</b> if this Order's payment has already been refunded,
     * returns that existing refund's details ({@code alreadyRefunded = true})
     * WITHOUT calling Razorpay again — a double-click or retried request is
     * always safe.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the order has no payment record at all
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the payment isn't in a refundable (PAID) state
     */
    RefundResultResponse refundOrder(Long orderId);

    /** Same contract as {@link #refundOrder}, for a Booking's payment. */
    RefundResultResponse refundBooking(Long bookingId);
}
