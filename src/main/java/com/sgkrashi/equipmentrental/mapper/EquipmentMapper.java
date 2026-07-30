package com.sgkrashi.equipmentrental.mapper;

import com.sgkrashi.equipmentrental.dto.response.EquipmentDetailResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;
import com.sgkrashi.equipmentrental.entity.Equipment;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EquipmentMapper {

    public EquipmentSummaryResponse toSummary(Equipment equipment, String thumbnailUrl) {
        return new EquipmentSummaryResponse(
                equipment.getId(),
                equipment.getName(),
                equipment.getSlug(),
                equipment.getCategory(),
                equipment.getDailyRate(),
                equipment.isAvailable(),
                thumbnailUrl,
                equipment.getAvgRating(),
                equipment.getReviewCount(),
                equipment.isActive()
        );
    }

    public EquipmentDetailResponse toDetail(Equipment equipment, List<MediaAssetResponse> media) {
        return new EquipmentDetailResponse(
                equipment.getId(),
                equipment.getName(),
                equipment.getSlug(),
                equipment.getCategory(),
                equipment.getDescription(),
                equipment.getDailyRate(),
                equipment.isAvailable(),
                media,
                equipment.getAvgRating(),
                equipment.getReviewCount(),
                equipment.isActive()
        );
    }
}
