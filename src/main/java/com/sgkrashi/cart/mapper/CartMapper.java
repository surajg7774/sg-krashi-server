package com.sgkrashi.cart.mapper;

import com.sgkrashi.cart.dto.response.CartItemResponse;
import com.sgkrashi.cart.dto.response.CartResponse;
import com.sgkrashi.cart.entity.CartItem;
import com.sgkrashi.productstore.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class CartMapper {

    public CartItemResponse toItemResponse(CartItem item, String thumbnailUrl) {
        Product product = item.getProduct();
        BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                thumbnailUrl,
                product.getPrice(),
                item.getQuantity(),
                lineTotal,
                product.getStockQty()
        );
    }

    public CartResponse toCartResponse(List<CartItem> items, Map<Long, String> thumbnailsByProductId) {
        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(item, thumbnailsByProductId.get(item.getProduct().getId())))
                .toList();

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int itemCount = itemResponses.stream().mapToInt(CartItemResponse::quantity).sum();

        return new CartResponse(itemResponses, subtotal, itemCount);
    }
}
