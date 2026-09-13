package com.sgkrashi.payout.dto.response;

import com.sgkrashi.payout.entity.PayoutLineType;

import java.math.BigDecimal;

/**
 * One line of a payout — either an EARNING (the farmer's share of one
 * DELIVERED order item) or a CLAWBACK (a negative reversal created when
 * that item's order was later refunded). {@code amount} fields are negative
 * on a CLAWBACK line; the frontend badges {@code lineType} rather than
 * inferring it from the sign.
 */
public record PayoutLineResponse(
        Long id,
        Long orderItemId,
        Long orderId,
        String orderNumber,
        String itemNameSnapshot,
        PayoutLineType lineType,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount
) {
}
