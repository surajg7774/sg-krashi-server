package com.sgkrashi.equipmentrental.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.common.util.SlugUtil;
import com.sgkrashi.equipmentrental.dto.request.EquipmentAdminRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EquipmentServiceImpl implements EquipmentService {

    private static final String EQUIPMENT_OWNER_TYPE = "EQUIPMENT";
    private static final String ENTITY_TYPE_EQUIPMENT = "EQUIPMENT";

    private final EquipmentRepository equipmentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final EquipmentMapper equipmentMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final AuditLogService auditLogService;

    public EquipmentServiceImpl(
            EquipmentRepository equipmentRepository,
            MediaAssetRepository mediaAssetRepository,
            EquipmentMapper equipmentMapper,
            MediaAssetMapper mediaAssetMapper,
            AuditLogService auditLogService
    ) {
        this.equipmentRepository = equipmentRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.equipmentMapper = equipmentMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public PaginatedResponse<EquipmentSummaryResponse> listEquipment(String category, String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));

        boolean hasCategory = category != null && !category.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        Page<Equipment> equipmentPage;
        if (hasCategory && hasSearch) {
            equipmentPage = equipmentRepository.findByIsActiveTrueAndCategoryIgnoreCaseAndNameContainingIgnoreCase(category, search, pageable);
        } else if (hasSearch) {
            equipmentPage = equipmentRepository.findByIsActiveTrueAndNameContainingIgnoreCase(search, pageable);
        } else if (hasCategory) {
            equipmentPage = equipmentRepository.findByIsActiveTrueAndCategoryIgnoreCase(category, pageable);
        } else {
            equipmentPage = equipmentRepository.findByIsActiveTrue(pageable);
        }

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
        return buildDetailResponse(resolveEquipment(idOrSlug));
    }

    /** See {@code ProductServiceImpl.buildDetailResponse}'s Javadoc for why Admin create/update use this directly instead of {@link #getEquipmentDetail}. */
    private EquipmentDetailResponse buildDetailResponse(Equipment equipment) {
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

    @Override
    @Transactional
    public EquipmentDetailResponse createEquipment(EquipmentAdminRequest request) {
        Equipment equipment = new Equipment();
        applyRequest(equipment, request);
        Equipment saved = equipmentRepository.save(equipment);
        EquipmentDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.EQUIPMENT_CREATED, ENTITY_TYPE_EQUIPMENT, saved.getId(), null, after);
        return after;
    }

    @Override
    @Transactional
    public EquipmentDetailResponse updateEquipment(Long id, EquipmentAdminRequest request) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        EquipmentDetailResponse before = buildDetailResponse(equipment);
        applyRequest(equipment, request);
        Equipment saved = equipmentRepository.save(equipment);
        EquipmentDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.EQUIPMENT_UPDATED, ENTITY_TYPE_EQUIPMENT, id, before, after);
        return after;
    }

    @Override
    @Transactional
    public void deactivateEquipment(Long id) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        EquipmentDetailResponse before = buildDetailResponse(equipment);
        equipment.setActive(false);
        Equipment saved = equipmentRepository.save(equipment);
        auditLogService.record(AuditActions.EQUIPMENT_DEACTIVATED, ENTITY_TYPE_EQUIPMENT, id, before, buildDetailResponse(saved));
    }

    @Override
    public PaginatedResponse<EquipmentSummaryResponse> listEquipmentForAdmin(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));
        Page<Equipment> equipmentPage = equipmentRepository.findByNameContainingIgnoreCase(search == null ? "" : search, pageable);

        List<Long> equipmentIds = equipmentPage.getContent().stream().map(Equipment::getId).toList();
        Map<Long, String> thumbnails = batchThumbnails(equipmentIds);

        List<EquipmentSummaryResponse> items = equipmentPage.getContent().stream()
                .map(equipment -> equipmentMapper.toSummary(equipment, thumbnails.get(equipment.getId())))
                .toList();

        return PaginatedResponse.of(items, equipmentPage);
    }

    @Override
    public EquipmentDetailResponse getEquipmentForAdmin(Long id) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        return buildDetailResponse(equipment);
    }

    private void applyRequest(Equipment equipment, EquipmentAdminRequest request) {
        String slug = (request.slug() == null || request.slug().isBlank())
                ? SlugUtil.uniqueSlugFrom(request.name(), candidate -> !candidate.equals(equipment.getSlug()) && equipmentRepository.existsBySlug(candidate))
                : request.slug();

        equipment.setName(request.name());
        equipment.setSlug(slug);
        equipment.setCategory(request.category());
        equipment.setDescription(request.description());
        equipment.setDailyRate(request.dailyRate());
        equipment.setAvailable(request.isAvailable());
        equipment.setActive(request.isActive());
    }
}
