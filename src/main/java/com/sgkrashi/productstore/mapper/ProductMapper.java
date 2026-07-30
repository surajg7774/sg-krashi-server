package com.sgkrashi.productstore.mapper;

import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.productstore.dto.response.ProductDetailResponse;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.entity.ProductCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {

    public ProductSummaryResponse toSummary(Product product, String thumbnailUrl) {
        ProductCategory category = product.getCategory();
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getPrice(),
                product.isOrganicCertified(),
                product.getStockQty(),
                category != null ? category.getName() : null,
                thumbnailUrl,
                product.getAvgRating(),
                product.getReviewCount()
        );
    }

    public ProductDetailResponse toDetail(
            Product product,
            List<MediaAssetResponse> media,
            List<ProductSummaryResponse> relatedProducts
    ) {
        ProductCategory category = product.getCategory();
        ProductDetailResponse.ProductCategorySummary categorySummary = category != null
                ? new ProductDetailResponse.ProductCategorySummary(category.getId(), category.getName(), category.getSlug())
                : null;

        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getPrice(),
                product.isOrganicCertified(),
                product.getStockQty(),
                categorySummary,
                media,
                relatedProducts,
                product.getAvgRating(),
                product.getReviewCount()
        );
    }
}
