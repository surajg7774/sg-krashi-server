package com.sgkrashi.payment.repository;

import com.sgkrashi.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPayableTypeAndPayableId(String payableType, Long payableId);

    /**
     * Locks the row so a webhook's read-check-write idempotency sequence is
     * itself serialized against a concurrent duplicate delivery of the same
     * event, rather than relying on the terminal-status check alone.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.gatewayOrderId = :gatewayOrderId")
    Optional<Payment> findByGatewayOrderIdForUpdate(@Param("gatewayOrderId") String gatewayOrderId);
}
