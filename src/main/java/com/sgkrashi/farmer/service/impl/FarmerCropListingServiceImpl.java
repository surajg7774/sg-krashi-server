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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final CropListingService cropListingService;
    private final CropListingRepository cropListingRepository;

    public FarmerCropListingServiceImpl(CropListingService cropListingService, CropListingRepository cropListingRepository) {
        this.cropListingService = cropListingService;
        this.cropListingRepository = cropListingRepository;
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

    private CropListing requireOwnership(Long farmerId, Long id) {
        return cropListingRepository.findByIdAndFarmerId(id, farmerId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
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
