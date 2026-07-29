package com.sgkrashi.payment.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
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

import java.util.Optional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String PAYABLE_TYPE_ORDER = "ORDER";
    private static final String DEFAULT_CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentGatewayAdapter gatewayAdapter;
    private final CurrentUserProvider currentUserProvider;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            OrderService orderService,
            PaymentGatewayAdapter gatewayAdapter,
            CurrentUserProvider currentUserProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.gatewayAdapter = gatewayAdapter;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public PaymentInitiationResponse initiatePayment(InitiatePaymentRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        Order order = orderService.getOrderEntityOrThrow(request.orderId());
        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("This order is not awaiting payment");
        }

        Optional<Payment> existing = paymentRepository.findByPayableTypeAndPayableId(PAYABLE_TYPE_ORDER, order.getId());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PAID) {
                throw new BusinessRuleException("This order has already been paid");
            }
            return toInitiationResponse(payment);
        }

        GatewayOrderResult gatewayOrder =
                gatewayAdapter.createOrder(order.getTotalAmount(), DEFAULT_CURRENCY, order.getOrderNumber());

        Payment payment = new Payment();
        payment.setPayableType(PAYABLE_TYPE_ORDER);
        payment.setPayableId(order.getId());
        payment.setGatewayOrderId(gatewayOrder.gatewayOrderId());
        payment.setAmount(gatewayOrder.amount());
        payment.setCurrency(gatewayOrder.currency());
        payment.setStatus(PaymentStatus.CREATED);
        Payment saved = paymentRepository.save(payment);

        return toInitiationResponse(saved);
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
            if (PAYABLE_TYPE_ORDER.equals(payment.getPayableType())) {
                orderService.markConfirmed(payment.getPayableId());
            }
        } else if (event.paymentFailed()) {
            payment.setGatewayPaymentId(event.gatewayPaymentId());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            if (PAYABLE_TYPE_ORDER.equals(payment.getPayableType())) {
                orderService.markPaymentFailed(payment.getPayableId());
            }
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
