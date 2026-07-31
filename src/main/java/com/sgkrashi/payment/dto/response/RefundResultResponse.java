package com.sgkrashi.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code alreadyRefunded} lets the frontend show a slightly different message ("already refunded" vs "refund processed") without treating the idempotent short-circuit as an error. */
public record RefundResultResponse(
        Long paymentId,
        String refundId,
        BigDecimal amount,
        String currency,
        Instant refundedAt,
        boolean alreadyRefunded
) {
}
