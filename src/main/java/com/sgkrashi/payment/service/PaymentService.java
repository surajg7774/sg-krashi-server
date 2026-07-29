package com.sgkrashi.payment.service;

import com.sgkrashi.payment.dto.request.InitiatePaymentRequest;
import com.sgkrashi.payment.dto.response.PaymentInitiationResponse;

public interface PaymentService {

    /**
     * Opens a gateway order against an existing PENDING_PAYMENT order owned by
     * the caller. Re-initiating a payment that's already CREATED returns the
     * same gateway order rather than opening a second one.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if the order doesn't exist or isn't the caller's
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the order is already paid or not payable
     */
    PaymentInitiationResponse initiatePayment(InitiatePaymentRequest request);

    /**
     * Verifies the webhook signature, then applies the event idempotently: a
     * payment already in a terminal status is left untouched even if the same
     * event is delivered again.
     *
     * @throws com.sgkrashi.common.exception.BusinessRuleException if the signature doesn't verify
     */
    void processWebhook(String rawBody, String signatureHeader);
}
