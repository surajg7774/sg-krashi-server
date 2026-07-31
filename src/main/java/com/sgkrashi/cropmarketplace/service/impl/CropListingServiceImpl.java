package com.sgkrashi.cropmarketplace.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.common.util.SlugUtil;
import com.sgkrashi.cropmarketplace.dto.request.CropListingAdminRequest;
import com.sgkrashi.cropmarketplace.dto.request.CropListingFilterRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.cropmarketplace.entity.CropCategory;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.cropmarketplace.mapper.CropListingMapper;
import com.sgkrashi.cropmarketplace.repository.CropCategoryRepository;
import com.sgkrashi.cropmarketplace.repository.CropListingRepository;
import com.sgkrashi.cropmarketplace.service.CropListingService;
import com.sgkrashi.cropmarketplace.specification.CropListingSpecifications;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.mapper.MediaAssetMapper;
import com.sgkrashi.media.repository.MediaAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Structurally mirrors {@code ProductServiceImpl} — see its Javadoc for the composition pattern. */
@Service
public class CropListingServiceImpl implements CropListingService {

    private static final String CROP_LISTING_OWNER_TYPE = "CROP_LISTING";
    private static final String ENTITY_TYPE_CROP_LISTING = "CROP_LISTING";
    private static final int MAX_RELATED_LISTINGS = 6;

    private final CropListingRepository cropListingRepository;
    private final CropCategoryRepository cropCategoryRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CropListingMapper cropListingMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final AuditLogService auditLogService;

    public CropListingServiceImpl(
            CropListingRepository cropListingRepository,
            CropCategoryRepository cropCategoryRepository,
            MediaAssetRepository mediaAssetRepository,
            CropListingMapper cropListingMapper,
            MediaAssetMapper mediaAssetMapper,
            AuditLogService auditLogService
    ) {
        this.cropListingRepository = cropListingRepository;
        this.cropCategoryRepository = cropCategoryRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.cropListingMapper = cropListingMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public PaginatedResponse<CropListingSummaryResponse> listCropListings(CropListingFilterRequest filter) {
        Specification<CropListing> spec = Specification.allOf(
                CropListingSpecifications.isActive(),
                CropListingSpecifications.hasCropType(filter.cropType()),
                CropListingSpecifications.priceGreaterThanOrEqual(filter.minPrice()),
                CropListingSpecifications.priceLessThanOrEqual(filter.maxPrice()),
                CropListingSpecifications.harvestDateFrom(filter.harvestDateFrom()),
                CropListingSpecifications.harvestDateTo(filter.harvestDateTo()),
                CropListingSpecifications.nameContains(filter.search()));

        Pageable pageable = PageRequest.of(
                Math.max(filter.page(), 0),
                filter.size() > 0 ? filter.size() : 20,
                Sort.by(Sort.Direction.ASC, "name"));

        Page<CropListing> listingPage = cropListingRepository.findAll(spec, pageable);

        List<Long> listingIds = listingPage.getContent().stream().map(CropListing::getId).toList();
        Map<Long, String> thumbnailsByListingId = batchThumbnails(listingIds);

        List<CropListingSummaryResponse> items = listingPage.getContent().stream()
                .map(listing -> cropListingMapper.toSummary(listing, thumbnailsByListingId.get(listing.getId())))
                .toList();

        return PaginatedResponse.of(items, listingPage);
    }

    @Override
    public CropListingDetailResponse getCropListingDetail(String idOrSlug) {
        return buildDetailResponse(resolveListing(idOrSlug));
    }

    /** See {@code ProductServiceImpl.buildDetailResponse}'s Javadoc for why Admin create/update use this directly instead of {@link #getCropListingDetail}. */
    private CropListingDetailResponse buildDetailResponse(CropListing listing) {
        List<MediaAssetResponse> media = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdOrderBySortOrderAsc(CROP_LISTING_OWNER_TYPE, listing.getId()).stream()
                .map(mediaAssetMapper::toResponse)
                .toList();

        List<CropListingSummaryResponse> relatedListings = buildRelatedListings(listing);

        return cropListingMapper.toDetail(listing, media, relatedListings);
    }

    private List<CropListingSummaryResponse> buildRelatedListings(CropListing listing) {
        if (listing.getCategory() == null) {
            return List.of();
        }
        List<CropListing> related = cropListingRepository.findTop6ByCategoryIdAndIsActiveTrueAndIdNot(
                listing.getCategory().getId(), listing.getId());

        List<Long> relatedIds = related.stream().map(CropListing::getId).toList();
        Map<Long, String> thumbnailsByListingId = batchThumbnails(relatedIds);

        return related.stream()
                .limit(MAX_RELATED_LISTINGS)
                .map(l -> cropListingMapper.toSummary(l, thumbnailsByListingId.get(l.getId())))
                .toList();
    }

    private Map<Long, String> batchThumbnails(List<Long> listingIds) {
        return mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(CROP_LISTING_OWNER_TYPE, listingIds).stream()
                .collect(Collectors.toMap(
                        MediaAsset::getOwnerId,
                        MediaAsset::getUrl,
                        (first, second) -> first));
    }

    private CropListing resolveListing(String idOrSlug) {
        return parseId(idOrSlug)
                .flatMap(cropListingRepository::findByIdAndIsActiveTrue)
                .or(() -> cropListingRepository.findBySlugAndIsActiveTrue(idOrSlug))
                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found: " + idOrSlug));
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
    public CropListingDetailResponse createCropListing(CropListingAdminRequest request) {
        CropListing listing = new CropListing();
        applyRequest(listing, request);
        CropListing saved = cropListingRepository.save(listing);
        CropListingDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.CROP_LISTING_CREATED, ENTITY_TYPE_CROP_LISTING, saved.getId(), null, after);
        return after;
    }

    @Override
    @Transactional
    public CropListingDetailResponse updateCropListing(Long id, CropListingAdminRequest request) {
        CropListing listing = cropListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
        CropListingDetailResponse before = buildDetailResponse(listing);
        applyRequest(listing, request);
        CropListing saved = cropListingRepository.save(listing);
        CropListingDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.CROP_LISTING_UPDATED, ENTITY_TYPE_CROP_LISTING, id, before, after);
        return after;
    }

    @Override
    @Transactional
    public void deactivateCropListing(Long id) {
        CropListing listing = cropListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
        CropListingDetailResponse before = buildDetailResponse(listing);
        listing.setActive(false);
        CropListing saved = cropListingRepository.save(listing);
        auditLogService.record(AuditActions.CROP_LISTING_DEACTIVATED, ENTITY_TYPE_CROP_LISTING, id, before, buildDetailResponse(saved));
    }

    @Override
    public PaginatedResponse<CropListingSummaryResponse> listCropListingsForAdmin(String search, int page, int size) {
        Specification<CropListing> spec = Specification.allOf(CropListingSpecifications.nameContains(search));
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));
        Page<CropListing> listingPage = cropListingRepository.findAll(spec, pageable);

        List<Long> listingIds = listingPage.getContent().stream().map(CropListing::getId).toList();
        Map<Long, String> thumbnailsByListingId = batchThumbnails(listingIds);

        List<CropListingSummaryResponse> items = listingPage.getContent().stream()
                .map(listing -> cropListingMapper.toSummary(listing, thumbnailsByListingId.get(listing.getId())))
                .toList();

        return PaginatedResponse.of(items, listingPage);
    }

    @Override
    public CropListingDetailResponse getCropListingForAdmin(Long id) {
        CropListing listing = cropListingRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
        return buildDetailResponse(listing);
    }

    private void applyRequest(CropListing listing, CropListingAdminRequest request) {
        CropCategory category = cropCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Crop category not found"));

        String slug = (request.slug() == null || request.slug().isBlank())
                ? SlugUtil.uniqueSlugFrom(request.name(), candidate -> !candidate.equals(listing.getSlug()) && cropListingRepository.existsBySlug(candidate))
                : request.slug();

        listing.setCategory(category);
        listing.setName(request.name());
        listing.setSlug(slug);
        listing.setDescription(request.description());
        listing.setQuantityAvailable(request.quantityAvailable());
        listing.setUnitPrice(request.unitPrice());
        listing.setHarvestDate(request.harvestDate());
        listing.setActive(request.isActive());
    }
}
