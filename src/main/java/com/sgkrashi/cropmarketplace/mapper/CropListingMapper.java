package com.sgkrashi.cropmarketplace.mapper;

import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.cropmarketplace.entity.CropCategory;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CropListingMapper {

    public CropListingSummaryResponse toSummary(CropListing listing, String thumbnailUrl) {
        CropCategory category = listing.getCategory();
        return new CropListingSummaryResponse(
                listing.getId(),
                listing.getName(),
                listing.getSlug(),
                listing.getUnitPrice(),
                listing.getQuantityAvailable(),
                listing.getHarvestDate(),
                category != null ? category.getName() : null,
                thumbnailUrl,
                listing.getAvgRating(),
                listing.getReviewCount(),
                listing.isActive()
        );
    }

    public CropListingDetailResponse toDetail(
            CropListing listing,
            List<MediaAssetResponse> media,
            List<CropListingSummaryResponse> relatedListings
    ) {
        CropCategory category = listing.getCategory();
        CropListingDetailResponse.CropCategorySummary categorySummary = category != null
                ? new CropListingDetailResponse.CropCategorySummary(category.getId(), category.getName(), category.getSlug())
                : null;

        return new CropListingDetailResponse(
                listing.getId(),
                listing.getName(),
                listing.getSlug(),
                listing.getDescription(),
                listing.getUnitPrice(),
                listing.getQuantityAvailable(),
                listing.getHarvestDate(),
                categorySummary,
                media,
                relatedListings,
                listing.getAvgRating(),
                listing.getReviewCount(),
                listing.isActive()
        );
    }
}
