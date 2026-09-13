package com.sgkrashi.payout.mapper;

import com.sgkrashi.payout.dto.response.AdminPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.AdminPayoutSummaryResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutSummaryResponse;
import com.sgkrashi.payout.dto.response.PayoutLineResponse;
import com.sgkrashi.payout.entity.FarmerPayout;
import com.sgkrashi.payout.entity.FarmerPayoutLine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PayoutMapper {

    public FarmerPayoutSummaryResponse toFarmerSummary(FarmerPayout payout) {
        return new FarmerPayoutSummaryResponse(
                payout.getId(),
                payout.getCycleStartDate(),
                payout.getCycleEndDate(),
                payout.getGrossAmount(),
                payout.getCommissionAmount(),
                payout.getNetAmount(),
                payout.getStatus(),
                payout.getApprovedAt(),
                payout.getPaidAt());
    }

    public FarmerPayoutDetailResponse toFarmerDetail(FarmerPayout payout, List<FarmerPayoutLine> lines) {
        return new FarmerPayoutDetailResponse(
                payout.getId(),
                payout.getCycleStartDate(),
                payout.getCycleEndDate(),
                payout.getGrossAmount(),
                payout.getCommissionAmount(),
                payout.getNetAmount(),
                payout.getStatus(),
                payout.getApprovedAt(),
                payout.getPaidAt(),
                lines.stream().map(this::toLine).toList());
    }

    public AdminPayoutSummaryResponse toAdminSummary(FarmerPayout payout, String farmerName, String farmerEmail) {
        return new AdminPayoutSummaryResponse(
                payout.getId(),
                payout.getFarmerId(),
                farmerName,
                farmerEmail,
                payout.getCycleStartDate(),
                payout.getCycleEndDate(),
                payout.getGrossAmount(),
                payout.getCommissionAmount(),
                payout.getNetAmount(),
                payout.getStatus(),
                payout.getApprovedAt(),
                payout.getPaidAt());
    }

    public AdminPayoutDetailResponse toAdminDetail(FarmerPayout payout, String farmerName, String farmerEmail, List<FarmerPayoutLine> lines) {
        return new AdminPayoutDetailResponse(
                payout.getId(),
                payout.getFarmerId(),
                farmerName,
                farmerEmail,
                payout.getCycleStartDate(),
                payout.getCycleEndDate(),
                payout.getGrossAmount(),
                payout.getCommissionAmount(),
                payout.getNetAmount(),
                payout.getStatus(),
                payout.getApprovedAt(),
                payout.getPaidAt(),
                lines.stream().map(this::toLine).toList());
    }

    private PayoutLineResponse toLine(FarmerPayoutLine line) {
        return new PayoutLineResponse(
                line.getId(),
                line.getOrderItem().getId(),
                line.getOrderItem().getOrder().getId(),
                line.getOrderItem().getOrder().getOrderNumber(),
                line.getOrderItem().getItemNameSnapshot(),
                line.getLineType(),
                line.getGrossAmount(),
                line.getCommissionAmount(),
                line.getNetAmount());
    }
}
