package com.sgkrashi.productstore.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sgkrashi.dairy.dto.DairyDtos.DairyDetailsResponse;
import com.sgkrashi.media.dto.response.MediaAssetResponse;

import java.math.BigDecimal;
import java.time.Instant;
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
        int reviewCount,
        boolean isActive,
        Instant createdAt,
        /** Only present for dairy products; left out of the JSON entirely for every other product. */
        @JsonInclude(JsonInclude.Include.NON_NULL) DairyDetailsResponse dairy
) {
    public record ProductCategorySummary(Long id, String name, String slug) {
    }
}
