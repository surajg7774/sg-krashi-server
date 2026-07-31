package com.sgkrashi.payment.gateway;

/** {@code status} is Razorpay's own refund status string (e.g. {@code "processed"}) — surfaced for logging/debugging, not parsed into an enum here since this module only acts on success-vs-exception, not intermediate refund states. */
public record GatewayRefundResult(String refundId, String status) {}
