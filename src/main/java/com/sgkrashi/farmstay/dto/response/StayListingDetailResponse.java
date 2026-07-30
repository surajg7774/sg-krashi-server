package com.sgkrashi.farmstay.dto.response;

import com.sgkrashi.media.dto.response.MediaAssetResponse;

import java.math.BigDecimal;
import java.util.List;

public record StayListingDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        int maxGuests,
        BigDecimal nightlyRate,
        List<String> amenities,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        boolean isAvailable,
        List<MediaAssetResponse> media
) {
}
