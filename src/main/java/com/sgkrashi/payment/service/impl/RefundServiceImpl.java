package com.sgkrashi.payment.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.service.BookingService;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.service.OrderService;
import com.sgkrashi.payment.dto.response.RefundResultResponse;
import com.sgkrashi.payment.entity.Payment;
import com.sgkrashi.payment.entity.PaymentStatus;
import com.sgkrashi.payment.gateway.GatewayRefundResult;
import com.sgkrashi.payment.gateway.PaymentGatewayAdapter;
import com.sgkrashi.payment.repository.PaymentRepository;
import com.sgkrashi.payment.service.RefundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The correctness-critical class of Module 16 — see {@link #processRefund}'s
 * Javadoc for the full idempotency writeup. Treated with the same rigor as
 * Module 6's original checkout transaction: explicit locking before the
 * check-then-act, and a real gateway call that must never fire twice for the
 * same logical refund.
 */
@Service
public class RefundServiceImpl implements RefundService {

    private static final String PAYABLE_TYPE_ORDER = "ORDER";
    private static final String PAYABLE_TYPE_BOOKING = "BOOKING";

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayAdapter gatewayAdapter;
    private final OrderService orderService;
    private final BookingService bookingService;
    private final AuditLogService auditLogService;

    public RefundServiceImpl(
            PaymentRepository paymentRepository,
            PaymentGatewayAdapter gatewayAdapter,
            OrderService orderService,
            BookingService bookingService,
            AuditLogService auditLogService
    ) {
        this.paymentRepository = paymentRepository;
        this.gatewayAdapter = gatewayAdapter;
        this.orderService = orderService;
        this.bookingService = bookingService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public RefundResultResponse refundOrder(Long orderId) {
        Order order = orderService.getOrderEntityOrThrow(orderId);
        return processRefund(PAYABLE_TYPE_ORDER, order.getId(), () -> orderService.markRefunded(order.getId()));
    }

    @Override
    @Transactional
    public RefundResultResponse refundBooking(Long bookingId) {
        Booking booking = bookingService.getBookingEntityOrThrow(bookingId);
        return processRefund(PAYABLE_TYPE_BOOKING, booking.getId(), () -> bookingService.markRefunded(booking.getId()));
    }

    /**
     * <ol>
     *   <li>Locks the {@code Payment} row ({@code findByPayableTypeAndPayableIdForUpdate})
     *       BEFORE inspecting its refund status — a plain read-then-write here
     *       would let two concurrent requests (a genuine double-click, or a
     *       client retry racing the first attempt's still-in-flight response)
     *       both observe "not yet refunded" and both call Razorpay. The lock
     *       serializes them: the second request's lock acquisition blocks
     *       until the first transaction commits, then re-reads the NOW-REFUNDED
     *       row and short-circuits below.</li>
     *   <li>The idempotency check ({@code status == REFUNDED}) runs and returns
     *       BEFORE any Razorpay call is made — never after. There is exactly one
     *       call site to the gateway's refund API in this whole class, and it
     *       is unreachable a second time for the same payment.</li>
     *   <li>Only a {@code PAID} payment may be refunded — {@code CREATED}
     *       (never captured) and {@code FAILED} payments have nothing to
     *       refund.</li>
     * </ol>
     */
    private RefundResultResponse processRefund(String payableType, Long payableId, Runnable markPayableRefunded) {
        Payment payment = paymentRepository.findByPayableTypeAndPayableIdForUpdate(payableType, payableId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for this " + payableType.toLowerCase()));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return new RefundResultResponse(
                    payment.getId(), payment.getRefundId(), payment.getAmount(), payment.getCurrency(),
                    payment.getRefundedAt(), true);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessRuleException("Only a paid payment can be refunded (current status: " + payment.getStatus() + ")");
        }

        PaymentSnapshot before = new PaymentSnapshot(payment.getStatus(), payment.getRefundId(), payment.getRefundedAt());

        GatewayRefundResult refundResult = gatewayAdapter.refund(payment.getGatewayPaymentId(), payment.getAmount());

        Instant refundedAt = Instant.now();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundId(refundResult.refundId());
        payment.setRefundedAt(refundedAt);
        paymentRepository.save(payment);

        markPayableRefunded.run();

        // Audited here, on the real-success path only — never on the
        // idempotent short-circuit above, since that path made no gateway
        // call and changed nothing.
        PaymentSnapshot after = new PaymentSnapshot(payment.getStatus(), payment.getRefundId(), payment.getRefundedAt());
        String action = PAYABLE_TYPE_ORDER.equals(payableType) ? AuditActions.ORDER_REFUNDED : AuditActions.BOOKING_REFUNDED;
        auditLogService.record(action, payableType, payableId, before, after);

        return new RefundResultResponse(payment.getId(), payment.getRefundId(), payment.getAmount(), payment.getCurrency(), refundedAt, false);
    }

    /** Minimal before/after audit snapshot — just the fields that actually change on a refund, not the full Payment entity. */
    private record PaymentSnapshot(PaymentStatus status, String refundId, Instant refundedAt) {
    }
}
