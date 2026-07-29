package com.sgkrashi.cart.mapper;

import com.sgkrashi.cart.dto.response.CartItemResponse;
import com.sgkrashi.cart.dto.response.CartResponse;
import com.sgkrashi.cart.entity.CartItem;
import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.productstore.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class CartMapper {

    /**
     * Two separate thumbnail maps (not one merged by ID) — a {@code Product}
     * and a {@code CropListing} can share the same numeric ID since they're
     * separate tables/ID spaces, so merging would risk one type's thumbnail
     * overwriting or masquerading as the other's.
     */
    public CartItemResponse toItemResponse(
            CartItem item,
            Map<Long, String> productThumbnails,
            Map<Long, String> cropListingThumbnails
    ) {
        if (item.getItemType() == ItemType.PRODUCT) {
            Product product = item.getProduct();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return new CartItemResponse(
                    item.getId(),
                    ItemType.PRODUCT,
                    product.getId(),
                    product.getName(),
                    product.getSlug(),
                    productThumbnails.get(product.getId()),
                    product.getPrice(),
                    item.getQuantity(),
                    lineTotal,
                    product.getStockQty()
            );
        }

        CropListing cropListing = item.getCropListing();
        BigDecimal lineTotal = cropListing.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                ItemType.CROP_LISTING,
                cropListing.getId(),
                cropListing.getName(),
                cropListing.getSlug(),
                cropListingThumbnails.get(cropListing.getId()),
                cropListing.getUnitPrice(),
                item.getQuantity(),
                lineTotal,
                cropListing.getQuantityAvailable()
        );
    }

    public CartResponse toCartResponse(
            List<CartItem> items,
            Map<Long, String> productThumbnails,
            Map<Long, String> cropListingThumbnails
    ) {
        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(item, productThumbnails, cropListingThumbnails))
                .toList();

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int itemCount = itemResponses.stream().mapToInt(CartItemResponse::quantity).sum();

        return new CartResponse(itemResponses, subtotal, itemCount);
    }
}
