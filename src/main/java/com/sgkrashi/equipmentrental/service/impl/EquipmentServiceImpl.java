package com.sgkrashi.equipmentrental.service.impl;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.equipmentrental.dto.response.EquipmentDetailResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;
import com.sgkrashi.equipmentrental.entity.Equipment;
import com.sgkrashi.equipmentrental.mapper.EquipmentMapper;
import com.sgkrashi.equipmentrental.repository.EquipmentRepository;
import com.sgkrashi.equipmentrental.service.EquipmentService;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.mapper.MediaAssetMapper;
import com.sgkrashi.media.repository.MediaAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EquipmentServiceImpl implements EquipmentService {

    private static final String EQUIPMENT_OWNER_TYPE = "EQUIPMENT";

    private final EquipmentRepository equipmentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final EquipmentMapper equipmentMapper;
    private final MediaAssetMapper mediaAssetMapper;

    public EquipmentServiceImpl(
            EquipmentRepository equipmentRepository,
            MediaAssetRepository mediaAssetRepository,
            EquipmentMapper equipmentMapper,
            MediaAssetMapper mediaAssetMapper
    ) {
        this.equipmentRepository = equipmentRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.equipmentMapper = equipmentMapper;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    @Override
    public PaginatedResponse<EquipmentSummaryResponse> listEquipment(String category, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));

        Page<Equipment> equipmentPage = (category == null || category.isBlank())
                ? equipmentRepository.findByIsActiveTrue(pageable)
                : equipmentRepository.findByIsActiveTrueAndCategoryIgnoreCase(category, pageable);

        List<Long> equipmentIds = equipmentPage.getContent().stream().map(Equipment::getId).toList();
        Map<Long, String> thumbnails = batchThumbnails(equipmentIds);

        List<EquipmentSummaryResponse> items = equipmentPage.getContent().stream()
                .map(equipment -> equipmentMapper.toSummary(equipment, thumbnails.get(equipment.getId())))
                .toList();

        return PaginatedResponse.of(items, equipmentPage);
    }

    @Override
    public List<String> listCategories() {
        return equipmentRepository.findDistinctCategories();
    }

    @Override
    public EquipmentDetailResponse getEquipmentDetail(String idOrSlug) {
        Equipment equipment = resolveEquipment(idOrSlug);

        List<MediaAssetResponse> media = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdOrderBySortOrderAsc(EQUIPMENT_OWNER_TYPE, equipment.getId()).stream()
                .map(mediaAssetMapper::toResponse)
                .toList();

        return equipmentMapper.toDetail(equipment, media);
    }

    private Map<Long, String> batchThumbnails(List<Long> equipmentIds) {
        return mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(EQUIPMENT_OWNER_TYPE, equipmentIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));
    }

    private Equipment resolveEquipment(String idOrSlug) {
        return parseId(idOrSlug)
                .flatMap(equipmentRepository::findByIdAndIsActiveTrue)
                .or(() -> equipmentRepository.findBySlugAndIsActiveTrue(idOrSlug))
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found: " + idOrSlug));
    }

    private Optional<Long> parseId(String idOrSlug) {
        try {
            return Optional.of(Long.valueOf(idOrSlug));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
