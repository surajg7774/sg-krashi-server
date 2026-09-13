package com.sgkrashi.payout.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One payout batch for one farmer. At most one {@code BATCHED} row should
 * ever exist per farmer at a time — the weekly job and any refund clawback
 * both reuse a farmer's currently-open {@code BATCHED} payout rather than
 * creating a second one (see {@code PayoutService.getOrCreateOpenBatch}).
 * Once a payout moves to {@code APPROVED}/{@code PAID} it is a frozen
 * historical record: nothing ever adds lines to it or edits its totals again.
 *
 * <p>{@code grossAmount}/{@code commissionAmount}/{@code netAmount} are
 * always a derived sum of this payout's own {@link FarmerPayoutLine} rows
 * (see {@code PayoutService.recomputeTotals}), never accumulated
 * independently — so they can never drift from what the line items actually
 * add up to.
 *
 * <p>{@code cycleStartDate}/{@code cycleEndDate} are bookkeeping only (the
 * span since this batch was opened until it was last added to), not a query
 * filter — the weekly job sweeps whatever is currently unbatched each run
 * rather than filtering by a calendar window.
 */
@Entity
@Table(name = "farmer_payouts")
public class FarmerPayout extends BaseEntity {

    @Column(name = "farmer_id", nullable = false)
    private Long farmerId;

    @Column(name = "cycle_start_date", nullable = false)
    private LocalDate cycleStartDate;

    @Column(name = "cycle_end_date", nullable = false)
    private LocalDate cycleEndDate;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    /** Admin who approved this batch — null until {@code status} reaches APPROVED. */
    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public Long getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(Long farmerId) {
        this.farmerId = farmerId;
    }

    public LocalDate getCycleStartDate() {
        return cycleStartDate;
    }

    public void setCycleStartDate(LocalDate cycleStartDate) {
        this.cycleStartDate = cycleStartDate;
    }

    public LocalDate getCycleEndDate() {
        return cycleEndDate;
    }

    public void setCycleEndDate(LocalDate cycleEndDate) {
        this.cycleEndDate = cycleEndDate;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public void setCommissionAmount(BigDecimal commissionAmount) {
        this.commissionAmount = commissionAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}
