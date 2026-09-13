package com.sgkrashi.farmer.service.impl;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.cropmarketplace.dto.request.CropListingAdminRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingDetailResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.cropmarketplace.repository.CropListingRepository;
import com.sgkrashi.cropmarketplace.service.CropListingService;
import com.sgkrashi.farmer.dto.request.FarmerCropListingRequest;
import com.sgkrashi.farmer.service.FarmerCropListingService;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.media.service.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Wraps {@link CropListingService} rather than duplicating its create/update/
 * query logic — see this package's report for the reuse assessment. The only
 * genuinely new logic here is ownership resolution: {@link
 * #requireOwnership} throws the same {@code ResourceNotFoundException} (404,
 * never 403/{@code AccessDeniedException}) whether the listing doesn't exist
 * at all or belongs to a different farmer, so probing a manipulated id can't
 * distinguish the two — the exact pattern Module 4's customer addresses
 * established.
 */
@Service
public class FarmerCropListingServiceImpl implements FarmerCropListingService {

    /** Same literal every other module (Order/Cart/Recommendation/CropListingService) already uses for this owner type — must match exactly, since {@code MediaAssetRepository} lookups are a free-text string match, not a real FK. */
    private static final String CROP_LISTING_OWNER_TYPE = "CROP_LISTING";

    private final CropListingService cropListingService;
    private final CropListingRepository cropListingRepository;
    private final MediaService mediaService;
    private final MediaAssetRepository mediaAssetRepository;

    public FarmerCropListingServiceImpl(
            CropListingService cropListingService,
            CropListingRepository cropListingRepository,
            MediaService mediaService,
            MediaAssetRepository mediaAssetRepository
    ) {
        this.cropListingService = cropListingService;
        this.cropListingRepository = cropListingRepository;
        this.mediaService = mediaService;
        this.mediaAssetRepository = mediaAssetRepository;
    }

    @Override
    public PaginatedResponse<CropListingSummaryResponse> listOwnListings(Long farmerId, String search, int page, int size) {
        return cropListingService.listCropListingsForFarmer(search, farmerId, page, size);
    }

    @Override
    public CropListingDetailResponse getOwnListing(Long farmerId, Long id) {
        requireOwnership(farmerId, id);
        return cropListingService.getCropListingForAdmin(id);
    }

    @Override
    @Transactional
    public CropListingDetailResponse createOwnListing(Long farmerId, FarmerCropListingRequest request) {
        return cropListingService.createCropListing(toAdminRequest(request, true), farmerId);
    }

    @Override
    @Transactional
    public CropListingDetailResponse updateOwnListing(Long farmerId, Long id, FarmerCropListingRequest request) {
        CropListing existing = requireOwnership(farmerId, id);
        return cropListingService.updateCropListing(id, toAdminRequest(request, existing.isActive()));
    }

    @Override
    @Transactional
    public void deactivateOwnListing(Long farmerId, Long id) {
        requireOwnership(farmerId, id);
        cropListingService.deactivateCropListing(id);
    }

    /**
     * Ownership check first (404 on mismatch, same as every other method
     * here), then delegates straight to {@code MediaService.upload} — the
     * exact same validation and storage path Admin catalog uploads use,
     * just with {@code ownerId} pinned to a listing this Farmer actually owns.
     */
    @Override
    public MediaAssetResponse uploadOwnListingMedia(Long farmerId, Long id, MultipartFile file) {
        requireOwnership(farmerId, id);
        return mediaService.upload(file, CROP_LISTING_OWNER_TYPE, id);
    }

    /**
     * Ownership check on both the listing and the media asset before
     * delegating to {@code MediaService.delete} — a genuine hard delete that
     * also removes the underlying Cloudinary file (see that method's Javadoc).
     */
    @Override
    @Transactional
    public void deleteOwnListingMedia(Long farmerId, Long id, Long mediaId) {
        requireOwnership(farmerId, id);
        requireMediaOwnership(id, mediaId);
        mediaService.delete(mediaId);
    }

    @Override
    @Transactional
    public MediaAssetResponse reorderOwnListingMedia(Long farmerId, Long id, Long mediaId, int sortOrder) {
        requireOwnership(farmerId, id);
        requireMediaOwnership(id, mediaId);
        return mediaService.updateSortOrder(mediaId, sortOrder);
    }

    private CropListing requireOwnership(Long farmerId, Long id) {
        return cropListingRepository.findByIdAndFarmerId(id, farmerId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
    }

    /**
     * {@code MediaAsset} has no FK to its owner — it's a deliberate
     * polymorphic-lite association (see that entity's Javadoc) — so {@code
     * MediaService.delete}/{@code updateSortOrder} trust whatever id they're
     * given, which is correct for Admin (who may act on anything) but not
     * here: without this check a Farmer could delete or reorder any media
     * asset on the platform just by guessing/enumerating ids. Same
     * never-distinguish-the-two 404 convention as {@link #requireOwnership}.
     */
    private void requireMediaOwnership(Long listingId, Long mediaId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        if (!CROP_LISTING_OWNER_TYPE.equals(asset.getOwnerType()) || !listingId.equals(asset.getOwnerId())) {
            throw new ResourceNotFoundException("Photo not found");
        }
    }

    /** {@code isActive} is never taken from the Farmer's request — it's preserved from the existing row (update) or forced true (create); toggling it is the DELETE endpoint's job only. */
    private CropListingAdminRequest toAdminRequest(FarmerCropListingRequest request, boolean isActive) {
        return new CropListingAdminRequest(
                request.categoryId(),
                request.name(),
                request.slug(),
                request.description(),
                request.quantityAvailable(),
                request.unitPrice(),
                request.harvestDate(),
                request.isOrganicCertified(),
                isActive);
    }
}
