package com.sgkrashi.notification.event;

import java.math.BigDecimal;

public record OrderPlacedEvent(Long orderId, Long userId, String orderNumber, BigDecimal totalAmount) {
}
