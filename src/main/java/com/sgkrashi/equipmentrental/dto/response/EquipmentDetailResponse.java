package com.sgkrashi.equipmentrental.dto.response;

import com.sgkrashi.media.dto.response.MediaAssetResponse;

import java.math.BigDecimal;
import java.util.List;

public record EquipmentDetailResponse(
        Long id,
        String name,
        String slug,
        String category,
        String description,
        BigDecimal dailyRate,
        boolean isAvailable,
        List<MediaAssetResponse> media
) {
}
