package com.sgkrashi.payment.dto.response;

import java.math.BigDecimal;

/** Everything the frontend needs to open Razorpay's checkout.js modal. */
public record PaymentInitiationResponse(
        Long paymentId,
        String gatewayOrderId,
        BigDecimal amount,
        String currency,
        String razorpayKeyId
) {}
