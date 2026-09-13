package com.sgkrashi.payout.entity;

import com.sgkrashi.order.entity.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only line item linking one {@code order_item} to the {@link
 * FarmerPayout} batch that covers it — same "immutable audit-trail row, no
 * {@code BaseEntity}" shape as {@code OrderStatusHistory}. Never updated
 * once created; a refund after the fact adds a new {@code CLAWBACK} sibling
 * row instead of editing this one (see {@code PayoutService.handleOrderRefunded}).
 *
 * <p>{@code (order_item_id, line_type)} is unique at the database level
 * (V29 migration) — the defensive second layer behind each write path's own
 * exclusion query, same double-guarantee pattern {@code BookingLock} uses.
 * This is why the uniqueness is on the pair rather than on {@code
 * order_item_id} alone: an item can have at most one {@code EARNING} line
 * ever, AND at most one {@code CLAWBACK} line ever, but legitimately both
 * (one of each) if it was paid out and then refunded.
 */
@Entity
@Table(name = "farmer_payout_lines")
public class FarmerPayoutLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", nullable = false)
    private FarmerPayout payout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private PayoutLineType lineType;

    /** Negative for a CLAWBACK line — see this class's Javadoc. */
    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FarmerPayout getPayout() {
        return payout;
    }

    public void setPayout(FarmerPayout payout) {
        this.payout = payout;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(OrderItem orderItem) {
        this.orderItem = orderItem;
    }

    public PayoutLineType getLineType() {
        return lineType;
    }

    public void setLineType(PayoutLineType lineType) {
        this.lineType = lineType;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
