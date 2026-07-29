package com.sgkrashi.order.mapper;

import com.sgkrashi.order.dto.response.OrderItemResponse;
import com.sgkrashi.order.dto.response.OrderResponse;
import com.sgkrashi.order.dto.response.OrderStatusEventResponse;
import com.sgkrashi.order.dto.response.OrderSummaryResponse;
import com.sgkrashi.order.entity.Order;
import com.sgkrashi.order.entity.OrderItem;
import com.sgkrashi.order.entity.OrderStatusHistory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderMapper {

    public OrderItemResponse toItemResponse(OrderItem item, String thumbnailUrl) {
        return new OrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                thumbnailUrl,
                item.getUnitPriceSnapshot(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }

    public OrderStatusEventResponse toStatusEventResponse(OrderStatusHistory event) {
        return new OrderStatusEventResponse(event.getStatus(), event.getNote(), event.getCreatedAt());
    }

    public OrderResponse toOrderResponse(
            Order order,
            List<OrderItem> items,
            List<OrderStatusHistory> history,
            Map<Long, String> thumbnailsByProductId
    ) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(item, thumbnailsByProductId.get(item.getProduct().getId())))
                .toList();

        List<OrderStatusEventResponse> historyResponses = history.stream()
                .map(this::toStatusEventResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getShippingLine1(),
                order.getShippingLine2(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingPincode(),
                itemResponses,
                historyResponses,
                order.getCreatedAt()
        );
    }

    public OrderSummaryResponse toSummaryResponse(Order order, int itemCount) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                itemCount,
                order.getCreatedAt()
        );
    }
}
