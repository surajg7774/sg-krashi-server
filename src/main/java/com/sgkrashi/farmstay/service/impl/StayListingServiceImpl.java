package com.sgkrashi.farmstay.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.common.util.SlugUtil;
import com.sgkrashi.farmstay.dto.request.StayListingAdminRequest;
import com.sgkrashi.farmstay.dto.response.StayListingDetailResponse;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;
import com.sgkrashi.farmstay.entity.StayListing;
import com.sgkrashi.farmstay.mapper.StayListingMapper;
import com.sgkrashi.farmstay.repository.StayListingRepository;
import com.sgkrashi.farmstay.service.StayListingService;
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

/** Structurally mirrors EquipmentServiceImpl — same "simple catalog" scale/shape. */
@Service
public class StayListingServiceImpl implements StayListingService {

    private static final String STAY_OWNER_TYPE = "STAY";
    private static final String ENTITY_TYPE_STAY_LISTING = "STAY_LISTING";

    private final StayListingRepository stayListingRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final StayListingMapper stayListingMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final AuditLogService auditLogService;

    public StayListingServiceImpl(
            StayListingRepository stayListingRepository,
            MediaAssetRepository mediaAssetRepository,
            StayListingMapper stayListingMapper,
            MediaAssetMapper mediaAssetMapper,
            AuditLogService auditLogService
    ) {
        this.stayListingRepository = stayListingRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.stayListingMapper = stayListingMapper;
        this.mediaAssetMapper = mediaAssetMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public PaginatedResponse<StayListingSummaryResponse> listStays(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));
        Page<StayListing> listingPage = (search == null || search.isBlank())
                ? stayListingRepository.findByIsActiveTrue(pageable)
                : stayListingRepository.findByIsActiveTrueAndNameContainingIgnoreCase(search, pageable);

        List<Long> listingIds = listingPage.getContent().stream().map(StayListing::getId).toList();
        Map<Long, String> thumbnails = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(STAY_OWNER_TYPE, listingIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));

        List<StayListingSummaryResponse> items = listingPage.getContent().stream()
                .map(listing -> stayListingMapper.toSummary(listing, thumbnails.get(listing.getId())))
                .toList();

        return PaginatedResponse.of(items, listingPage);
    }

    @Override
    public StayListingDetailResponse getStayDetail(String idOrSlug) {
        return buildDetailResponse(resolveListing(idOrSlug));
    }

    /** See {@code ProductServiceImpl.buildDetailResponse}'s Javadoc for why Admin create/update use this directly instead of {@link #getStayDetail}. */
    private StayListingDetailResponse buildDetailResponse(StayListing listing) {
        List<MediaAssetResponse> media = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdOrderBySortOrderAsc(STAY_OWNER_TYPE, listing.getId()).stream()
                .map(mediaAssetMapper::toResponse)
                .toList();

        return stayListingMapper.toDetail(listing, media);
    }

    private StayListing resolveListing(String idOrSlug) {
        return parseId(idOrSlug)
                .flatMap(stayListingRepository::findByIdAndIsActiveTrue)
                .or(() -> stayListingRepository.findBySlugAndIsActiveTrue(idOrSlug))
                .orElseThrow(() -> new ResourceNotFoundException("Stay listing not found: " + idOrSlug));
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
    public StayListingDetailResponse createStayListing(StayListingAdminRequest request) {
        StayListing listing = new StayListing();
        applyRequest(listing, request);
        StayListing saved = stayListingRepository.save(listing);
        StayListingDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.STAY_LISTING_CREATED, ENTITY_TYPE_STAY_LISTING, saved.getId(), null, after);
        return after;
    }

    @Override
    @Transactional
    public StayListingDetailResponse updateStayListing(Long id, StayListingAdminRequest request) {
        StayListing listing = stayListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stay listing not found"));
        StayListingDetailResponse before = buildDetailResponse(listing);
        applyRequest(listing, request);
        StayListing saved = stayListingRepository.save(listing);
        StayListingDetailResponse after = buildDetailResponse(saved);
        auditLogService.record(AuditActions.STAY_LISTING_UPDATED, ENTITY_TYPE_STAY_LISTING, id, before, after);
        return after;
    }

    @Override
    @Transactional
    public void deactivateStayListing(Long id) {
        StayListing listing = stayListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stay listing not found"));
        StayListingDetailResponse before = buildDetailResponse(listing);
        listing.setActive(false);
        StayListing saved = stayListingRepository.save(listing);
        auditLogService.record(AuditActions.STAY_LISTING_DEACTIVATED, ENTITY_TYPE_STAY_LISTING, id, before, buildDetailResponse(saved));
    }

    @Override
    public PaginatedResponse<StayListingSummaryResponse> listStaysForAdmin(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));
        Page<StayListing> listingPage = stayListingRepository.findByNameContainingIgnoreCase(search == null ? "" : search, pageable);

        List<Long> listingIds = listingPage.getContent().stream().map(StayListing::getId).toList();
        Map<Long, String> thumbnails = mediaAssetRepository
                .findByOwnerTypeAndOwnerIdInOrderBySortOrderAsc(STAY_OWNER_TYPE, listingIds).stream()
                .collect(Collectors.toMap(MediaAsset::getOwnerId, MediaAsset::getUrl, (first, second) -> first));

        List<StayListingSummaryResponse> items = listingPage.getContent().stream()
                .map(listing -> stayListingMapper.toSummary(listing, thumbnails.get(listing.getId())))
                .toList();

        return PaginatedResponse.of(items, listingPage);
    }

    @Override
    public StayListingDetailResponse getStayForAdmin(Long id) {
        StayListing listing = stayListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stay listing not found"));
        return buildDetailResponse(listing);
    }

    private void applyRequest(StayListing listing, StayListingAdminRequest request) {
        String slug = (request.slug() == null || request.slug().isBlank())
                ? SlugUtil.uniqueSlugFrom(request.name(), candidate -> !candidate.equals(listing.getSlug()) && stayListingRepository.existsBySlug(candidate))
                : request.slug();

        listing.setName(request.name());
        listing.setSlug(slug);
        listing.setDescription(request.description());
        listing.setMaxGuests(request.maxGuests());
        listing.setNightlyRate(request.nightlyRate());
        listing.setAmenities(request.amenities() == null ? "" : String.join(",", request.amenities()));
        listing.setAddressLine1(request.addressLine1());
        listing.setAddressLine2(request.addressLine2());
        listing.setCity(request.city());
        listing.setState(request.state());
        listing.setPincode(request.pincode());
        listing.setAvailable(request.isAvailable());
        listing.setActive(request.isActive());
    }
}
