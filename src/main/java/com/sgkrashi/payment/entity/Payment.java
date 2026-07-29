package com.sgkrashi.payment.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Generic payment record keyed by {@code payableType}/{@code payableId} rather
 * than a direct FK to {@code Order} — this module is the only payable type today,
 * but the same table is meant to be reused by Module 8/9 booking payments later
 * without a schema change (mirrors the {@code MediaAsset} owner-type pattern).
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "payable_type", nullable = false, length = 30)
    private String payableType;

    @Column(name = "payable_id", nullable = false)
    private Long payableId;

    @Column(name = "gateway_order_id", nullable = false, unique = true, length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", unique = true, length = 100)
    private String gatewayPaymentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    public String getPayableType() {
        return payableType;
    }

    public void setPayableType(String payableType) {
        this.payableType = payableType;
    }

    public Long getPayableId() {
        return payableId;
    }

    public void setPayableId(Long payableId) {
        this.payableId = payableId;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public void setGatewayOrderId(String gatewayOrderId) {
        this.gatewayOrderId = gatewayOrderId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
}
