package com.sgkrashi.payment.gateway;

import java.math.BigDecimal;

public interface PaymentGatewayAdapter {

    /** Opens an order with the gateway ahead of the customer paying against it. */
    GatewayOrderResult createOrder(BigDecimal amount, String currency, String receipt);

    /**
     * Verifies that a webhook delivery genuinely came from the gateway by
     * checking its signature header against the raw (unparsed) request body.
     * Must run BEFORE the body is trusted for anything else.
     */
    boolean verifySignature(String rawBody, String signatureHeader);

    /** Only call after {@link #verifySignature} has returned true. */
    GatewayWebhookEvent parseWebhookEvent(String rawBody);

    /** The publishable key ID the frontend needs to open the gateway's checkout widget. */
    String getPublicKeyId();

    /**
     * Issues a full refund against an already-captured payment. {@code amount}
     * is the same rupee amount originally captured — this module only supports
     * full refunds, never partial. Throws if the gateway rejects the refund
     * (e.g. already refunded on Razorpay's side, invalid payment id); callers
     * must check {@code Payment}'s own refund status BEFORE calling this, since
     * this call itself is not the idempotency guard — see {@code RefundService}.
     */
    GatewayRefundResult refund(String gatewayPaymentId, BigDecimal amount);
}
