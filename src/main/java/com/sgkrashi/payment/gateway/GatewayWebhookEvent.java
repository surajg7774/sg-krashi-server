package com.sgkrashi.payment.gateway;

/**
 * Normalized shape of whatever event the gateway sent — keeps Razorpay's own
 * JSON envelope out of the service layer so a future gateway (or a mocked one
 * in tests) can be swapped in without touching {@code PaymentServiceImpl}.
 */
public record GatewayWebhookEvent(
        String eventType,
        String gatewayOrderId,
        String gatewayPaymentId,
        boolean paymentCaptured,
        boolean paymentFailed
) {}
