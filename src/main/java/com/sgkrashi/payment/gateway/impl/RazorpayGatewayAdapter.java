package com.sgkrashi.payment.gateway.impl;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.sgkrashi.payment.gateway.GatewayOrderResult;
import com.sgkrashi.payment.gateway.GatewayRefundResult;
import com.sgkrashi.payment.gateway.GatewayWebhookEvent;
import com.sgkrashi.payment.gateway.PaymentGatewayAdapter;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Razorpay implementation of {@link PaymentGatewayAdapter}. Signature
 * verification is done manually via {@code HmacSHA256} (Razorpay's documented
 * webhook scheme: hex-encoded HMAC-SHA256 of the raw body, keyed with the
 * webhook secret) rather than through the SDK's own verification helper, so
 * behavior here doesn't depend on the exact SDK version resolved at build time.
 */
@Component
public class RazorpayGatewayAdapter implements PaymentGatewayAdapter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayGatewayAdapter(
            @Value("${app.razorpay.key-id}") String keyId,
            @Value("${app.razorpay.key-secret}") String keySecret,
            @Value("${app.razorpay.webhook-secret}") String webhookSecret
    ) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public GatewayOrderResult createOrder(BigDecimal amount, String currency, String receipt) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject request = new JSONObject();
            // Razorpay amounts are always the smallest currency unit (paise for INR).
            request.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValueExact());
            request.put("currency", currency);
            request.put("receipt", receipt);
            request.put("payment_capture", 1);

            com.razorpay.Order order = client.orders.create(request);
            return new GatewayOrderResult(order.get("id"), amount, currency);
        } catch (RazorpayException e) {
            throw new IllegalStateException("Unable to create Razorpay order", e);
        }
    }

    @Override
    public boolean verifySignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String computed = computeHmac(rawBody);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public GatewayWebhookEvent parseWebhookEvent(String rawBody) {
        JSONObject json = new JSONObject(rawBody);
        String eventType = json.optString("event", "");

        JSONObject paymentEntity = json.getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String gatewayOrderId = paymentEntity.optString("order_id", null);
        String gatewayPaymentId = paymentEntity.optString("id", null);

        boolean captured = "payment.captured".equals(eventType);
        boolean failed = "payment.failed".equals(eventType);

        return new GatewayWebhookEvent(eventType, gatewayOrderId, gatewayPaymentId, captured, failed);
    }

    @Override
    public String getPublicKeyId() {
        return keyId;
    }

    /**
     * Full refund only (Module 16 scope) — {@code amount} is passed explicitly
     * (in paise, same conversion as {@link #createOrder}) rather than omitted,
     * even though omitting it would also mean "refund the full amount" per
     * Razorpay's API: being explicit here means a future partial-refund bug
     * elsewhere can never accidentally under- or over-refund silently.
     */
    @Override
    public GatewayRefundResult refund(String gatewayPaymentId, BigDecimal amount) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject request = new JSONObject();
            request.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValueExact());

            Refund refund = client.payments.refund(gatewayPaymentId, request);
            return new GatewayRefundResult(refund.get("id"), refund.get("status"));
        } catch (RazorpayException e) {
            throw new IllegalStateException("Unable to process Razorpay refund for payment " + gatewayPaymentId, e);
        }
    }

    private String computeHmac(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute webhook signature", e);
        }
    }
}
