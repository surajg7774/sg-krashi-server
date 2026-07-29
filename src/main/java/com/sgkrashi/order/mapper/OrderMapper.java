package com.sgkrashi.order.mapper;

import com.sgkrashi.common.entity.ItemType;
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

    /**
     * Two separate thumbnail maps — see {@code CartMapper.toItemResponse}'s
     * Javadoc for why a Product and a CropListing's thumbnails can't be merged
     * into one ID-keyed map.
     */
    public OrderItemResponse toItemResponse(
            OrderItem item,
            Map<Long, String> productThumbnails,
            Map<Long, String> cropListingThumbnails
    ) {
        Long itemId = item.getReferencedItemId();
        String thumbnailUrl = item.getItemType() == ItemType.PRODUCT
                ? productThumbnails.get(itemId)
                : cropListingThumbnails.get(itemId);

        return new OrderItemResponse(
                item.getId(),
                item.getItemType(),
                itemId,
                item.getItemNameSnapshot(),
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
            Map<Long, String> productThumbnails,
            Map<Long, String> cropListingThumbnails
    ) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(item, productThumbnails, cropListingThumbnails))
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
