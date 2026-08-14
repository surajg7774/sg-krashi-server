package com.sgkrashi.recommendation.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Normalizes both catalog types (Product and CropListing) into one shape so
 * the frontend never needs to know which produced a given item — mirrors
 * {@code CropDoctorService}'s response-normalization precedent.
 */
public record RecommendationResponse(List<RecommendationItem> items) {

    public record RecommendationItem(
            Long id,
            String itemType,
            String name,
            String slug,
            BigDecimal price,
            String thumbnailUrl,
            BigDecimal avgRating,
            int reviewCount
    ) {
    }
}
