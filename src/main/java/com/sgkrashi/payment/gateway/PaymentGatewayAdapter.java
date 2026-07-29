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
}
