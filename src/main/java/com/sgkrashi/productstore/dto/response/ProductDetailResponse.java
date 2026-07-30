package com.sgkrashi.productstore.dto.response;

import com.sgkrashi.media.dto.response.MediaAssetResponse;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        boolean isOrganicCertified,
        int stockQty,
        ProductCategorySummary category,
        List<MediaAssetResponse> media,
        List<ProductSummaryResponse> relatedProducts,
        BigDecimal avgRating,
        int reviewCount
) {
    public record ProductCategorySummary(Long id, String name, String slug) {
    }
}
