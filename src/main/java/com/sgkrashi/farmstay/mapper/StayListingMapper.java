package com.sgkrashi.farmstay.mapper;

import com.sgkrashi.farmstay.dto.response.StayListingDetailResponse;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;
import com.sgkrashi.farmstay.entity.StayListing;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class StayListingMapper {

    public StayListingSummaryResponse toSummary(StayListing listing, String thumbnailUrl) {
        return new StayListingSummaryResponse(
                listing.getId(),
                listing.getName(),
                listing.getSlug(),
                listing.getCity(),
                listing.getState(),
                listing.getMaxGuests(),
                listing.getNightlyRate(),
                listing.isAvailable(),
                thumbnailUrl,
                listing.getAvgRating(),
                listing.getReviewCount()
        );
    }

    public StayListingDetailResponse toDetail(StayListing listing, List<MediaAssetResponse> media) {
        return new StayListingDetailResponse(
                listing.getId(),
                listing.getName(),
                listing.getSlug(),
                listing.getDescription(),
                listing.getMaxGuests(),
                listing.getNightlyRate(),
                parseAmenities(listing.getAmenities()),
                listing.getAddressLine1(),
                listing.getAddressLine2(),
                listing.getCity(),
                listing.getState(),
                listing.getPincode(),
                listing.isAvailable(),
                media,
                listing.getAvgRating(),
                listing.getReviewCount()
        );
    }

    /** Amenities are stored as a simple comma-separated string — no separate table for a small, fixed-shape list. */
    private List<String> parseAmenities(String amenities) {
        if (amenities == null || amenities.isBlank()) {
            return List.of();
        }
        return Arrays.stream(amenities.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
