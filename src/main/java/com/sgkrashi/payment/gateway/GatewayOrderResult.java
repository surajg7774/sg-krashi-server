package com.sgkrashi.payment.gateway;

import java.math.BigDecimal;

public record GatewayOrderResult(String gatewayOrderId, BigDecimal amount, String currency) {}
