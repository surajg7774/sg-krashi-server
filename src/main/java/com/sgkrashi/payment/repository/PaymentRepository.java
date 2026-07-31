package com.sgkrashi.payment.repository;

import com.sgkrashi.payment.entity.Payment;
import com.sgkrashi.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPayableTypeAndPayableId(String payableType, Long payableId);

    /** Batch lookup for Admin order/booking list views — same map-by-id pattern as MediaAsset thumbnail batching elsewhere, avoiding one query per row. */
    List<Payment> findByPayableTypeAndPayableIdIn(String payableType, List<Long> payableIds);

    /** Admin dashboard revenue KPI — simple on-demand SUM, no materialized view (Year 1 scale per the architecture doc). */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status and p.createdAt >= :since")
    BigDecimal sumAmountByStatusSince(@Param("status") PaymentStatus status, @Param("since") Instant since);

    /**
     * Locks the row for the refund flow's own read-check-write idempotency
     * sequence — same reasoning as {@link #findByGatewayOrderIdForUpdate}: a
     * double-click or retried refund request must not both pass the "not yet
     * refunded" check before either has committed its REFUNDED write.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.payableType = :payableType and p.payableId = :payableId")
    Optional<Payment> findByPayableTypeAndPayableIdForUpdate(
            @Param("payableType") String payableType, @Param("payableId") Long payableId);

    /**
     * Locks the row so a webhook's read-check-write idempotency sequence is
     * itself serialized against a concurrent duplicate delivery of the same
     * event, rather than relying on the terminal-status check alone.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.gatewayOrderId = :gatewayOrderId")
    Optional<Payment> findByGatewayOrderIdForUpdate(@Param("gatewayOrderId") String gatewayOrderId);
}
