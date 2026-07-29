package com.sgkrashi.payment.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.booking.service.BookingService;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.service.OrderService;
import com.sgkrashi.payment.dto.request.InitiatePaymentRequest;
import com.sgkrashi.payment.dto.response.PaymentInitiationResponse;
import com.sgkrashi.payment.entity.Payment;
import com.sgkrashi.payment.entity.PaymentStatus;
import com.sgkrashi.payment.gateway.GatewayOrderResult;
import com.sgkrashi.payment.gateway.GatewayWebhookEvent;
import com.sgkrashi.payment.gateway.PaymentGatewayAdapter;
import com.sgkrashi.payment.repository.PaymentRepository;
import com.sgkrashi.payment.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reuses the exact same {@link Payment} entity/table for both Orders (Module
 * 6) and Bookings (Module 8) via {@code payableType}/{@code payableId} — this
 * is the first real proof that Module 6's genericity was worth building.
 * {@code PaymentController} and the webhook-signature/idempotency machinery
 * below are unchanged from Module 6; only this class's dispatch on
 * {@code payableType} is new, mirroring the branch Module 6 already had for
 * ORDER with a second branch for BOOKING.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String PAYABLE_TYPE_ORDER = "ORDER";
    private static final String PAYABLE_TYPE_BOOKING = "BOOKING";
    private static final String DEFAULT_CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final BookingService bookingService;
    private final PaymentGatewayAdapter gatewayAdapter;
    private final CurrentUserProvider currentUserProvider;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            OrderService orderService,
            BookingService bookingService,
            PaymentGatewayAdapter gatewayAdapter,
            CurrentUserProvider currentUserProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.bookingService = bookingService;
        this.gatewayAdapter = gatewayAdapter;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public PaymentInitiationResponse initiatePayment(InitiatePaymentRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        BigDecimal amount = PAYABLE_TYPE_ORDER.equals(request.payableType())
                ? resolveOrderAmount(request.payableId(), userId)
                : resolveBookingAmount(request.payableId(), userId);

        Optional<Payment> existing = paymentRepository.findByPayableTypeAndPayableId(request.payableType(), request.payableId());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PAID) {
                throw new BusinessRuleException("This has already been paid");
            }
            return toInitiationResponse(payment);
        }

        String receipt = request.payableType() + "-" + request.payableId();
        GatewayOrderResult gatewayOrder = gatewayAdapter.createOrder(amount, DEFAULT_CURRENCY, receipt);

        Payment payment = new Payment();
        payment.setPayableType(request.payableType());
        payment.setPayableId(request.payableId());
        payment.setGatewayOrderId(gatewayOrder.gatewayOrderId());
        payment.setAmount(gatewayOrder.amount());
        payment.setCurrency(gatewayOrder.currency());
        payment.setStatus(PaymentStatus.CREATED);
        Payment saved = paymentRepository.save(payment);

        return toInitiationResponse(saved);
    }

    private BigDecimal resolveOrderAmount(Long orderId, Long userId) {
        Order order = orderService.getOrderEntityOrThrow(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("This order is not awaiting payment");
        }
        return order.getTotalAmount();
    }

    private BigDecimal resolveBookingAmount(Long bookingId, Long userId) {
        Booking booking = bookingService.getBookingEntityOrThrow(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Booking not found");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("This booking is not awaiting payment");
        }
        return booking.getTotalPrice();
    }

    @Override
    @Transactional
    public void processWebhook(String rawBody, String signatureHeader) {
        if (!gatewayAdapter.verifySignature(rawBody, signatureHeader)) {
            throw new BusinessRuleException("Invalid webhook signature");
        }

        GatewayWebhookEvent event = gatewayAdapter.parseWebhookEvent(rawBody);
        if (event.gatewayOrderId() == null) {
            return;
        }

        Payment payment = paymentRepository.findByGatewayOrderIdForUpdate(event.gatewayOrderId()).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.CREATED) {
            // Unknown gateway order, or already-terminal payment — the latter means this
            // is a duplicate delivery of an event we've already applied. No-op either way.
            return;
        }

        if (event.paymentCaptured()) {
            payment.setGatewayPaymentId(event.gatewayPaymentId());
            payment.setStatus(PaymentStatus.PAID);
            paymentRepository.save(payment);
            markPayableConfirmed(payment);
        } else if (event.paymentFailed()) {
            payment.setGatewayPaymentId(event.gatewayPaymentId());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            markPayableFailed(payment);
        }
    }

    private void markPayableConfirmed(Payment payment) {
        if (PAYABLE_TYPE_ORDER.equals(payment.getPayableType())) {
            orderService.markConfirmed(payment.getPayableId());
        } else if (PAYABLE_TYPE_BOOKING.equals(payment.getPayableType())) {
            bookingService.markConfirmed(payment.getPayableId());
        }
    }

    private void markPayableFailed(Payment payment) {
        if (PAYABLE_TYPE_ORDER.equals(payment.getPayableType())) {
            orderService.markPaymentFailed(payment.getPayableId());
        } else if (PAYABLE_TYPE_BOOKING.equals(payment.getPayableType())) {
            bookingService.markPaymentFailed(payment.getPayableId());
        }
    }

    private PaymentInitiationResponse toInitiationResponse(Payment payment) {
        return new PaymentInitiationResponse(
                payment.getId(),
                payment.getGatewayOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                gatewayAdapter.getPublicKeyId());
    }
}
