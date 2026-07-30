package com.sgkrashi.cropmarketplace.dto.response;

import com.sgkrashi.media.dto.response.MediaAssetResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CropListingDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        BigDecimal unitPrice,
        int quantityAvailable,
        LocalDate harvestDate,
        CropCategorySummary category,
        List<MediaAssetResponse> media,
        List<CropListingSummaryResponse> relatedListings,
        BigDecimal avgRating,
        int reviewCount
) {
    public record CropCategorySummary(Long id, String name, String slug) {
    }
}
