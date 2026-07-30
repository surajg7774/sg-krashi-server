package com.sgkrashi.farmstay.service.impl;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Structurally mirrors EquipmentServiceImpl — same "simple catalog" scale/shape. */
@Service
public class StayListingServiceImpl implements StayListingService {

    private static final String STAY_OWNER_TYPE = "STAY";

    private final StayListingRepository stayListingRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final StayListingMapper stayListingMapper;
    private final MediaAssetMapper mediaAssetMapper;

    public StayListingServiceImpl(
            StayListingRepository stayListingRepository,
            MediaAssetRepository mediaAssetRepository,
            StayListingMapper stayListingMapper,
            MediaAssetMapper mediaAssetMapper
    ) {
        this.stayListingRepository = stayListingRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.stayListingMapper = stayListingMapper;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    @Override
    public PaginatedResponse<StayListingSummaryResponse> listStays(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by(Sort.Direction.ASC, "name"));
        Page<StayListing> listingPage = stayListingRepository.findByIsActiveTrue(pageable);

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
        StayListing listing = resolveListing(idOrSlug);

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
}
