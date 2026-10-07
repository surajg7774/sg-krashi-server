package com.sgkrashi.farmstay.dto.response;

import com.sgkrashi.media.dto.response.MediaAssetResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What an unauthenticated visitor gets for a stay listing: everything in
 * {@link StayListingDetailResponse} except the street address ({@code addressLine1},
 * {@code addressLine2}) and {@code pincode}, which identify a private property.
 * City and state stay. The Admin endpoints keep returning the full
 * {@link StayListingDetailResponse}, since the admin edit form needs the address.
 */
public record PublicStayListingDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        int maxGuests,
        BigDecimal nightlyRate,
        List<String> amenities,
        String city,
        String state,
        boolean isAvailable,
        List<MediaAssetResponse> media,
        BigDecimal avgRating,
        int reviewCount,
        boolean isActive,
        Instant createdAt
) {
}
