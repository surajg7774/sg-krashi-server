package com.sgkrashi.payment.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.payment.dto.request.InitiatePaymentRequest;
import com.sgkrashi.payment.dto.response.PaymentInitiationResponse;
import com.sgkrashi.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiationResponse>> initiate(
            @Valid @RequestBody InitiatePaymentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.initiatePayment(request), "Payment initiated"));
    }

    /**
     * Public endpoint (see SecurityConfig) — Razorpay calls this directly with
     * no Authorization header. The raw body is bound as a String (not parsed
     * into a DTO) because signature verification must run against the exact
     * bytes Razorpay signed, before any JSON parsing happens.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        paymentService.processWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
