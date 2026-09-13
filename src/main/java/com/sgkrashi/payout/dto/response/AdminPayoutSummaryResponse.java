package com.sgkrashi.payout.dto.response;

import com.sgkrashi.payout.entity.PayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AdminPayoutSummaryResponse(
        Long id,
        Long farmerId,
        String farmerName,
        String farmerEmail,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        PayoutStatus status,
        Instant approvedAt,
        Instant paidAt
) {
}
