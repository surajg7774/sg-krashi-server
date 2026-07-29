package com.sgkrashi.cropmarketplace.service.impl;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.cropmarketplace.dto.request.CropListingFilterRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.cropmarketplace.mapper.CropListingMapper;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Structurally mirrors {@code ProductServiceImpl} — see its Javadoc for the composition pattern. */
@Service
public class CropListingServiceImpl implements CropListingService {

    private static final String CROP_LISTING_OWNER_TYPE = "CROP_LISTING";
    private static final int MAX_RELATED_LISTINGS = 6;

    private final CropListingRepository cropListingRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CropListingMapper cropListingMapper;
    private final MediaAssetMapper mediaAssetMapper;

    public CropListingServiceImpl(
            CropListingRepository cropListingRepository,
            MediaAssetRepository mediaAssetRepository,
            CropListingMapper cropListingMapper,
            MediaAssetMapper mediaAssetMapper
    ) {
        this.cropListingRepository = cropListingRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.cropListingMapper = cropListingMapper;
        this.mediaAssetMapper = mediaAssetMapper;
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
        CropListing listing = resolveListing(idOrSlug);

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
}
