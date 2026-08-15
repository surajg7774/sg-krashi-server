package com.sgkrashi.search.service.impl;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.cropmarketplace.dto.request.CropListingFilterRequest;
import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.cropmarketplace.service.CropListingService;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;
import com.sgkrashi.equipmentrental.service.EquipmentService;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;
import com.sgkrashi.farmstay.service.StayListingService;
import com.sgkrashi.productstore.dto.request.ProductFilterRequest;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;
import com.sgkrashi.productstore.service.ProductService;
import com.sgkrashi.search.dto.response.SearchResultsResponse;
import com.sgkrashi.search.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deliberately owns no entity/repository of its own — every query is
 * delegated to the existing Product/CropListing/Equipment/StayListing
 * services (Modules 5/7/8/9), reusing their established {@code search}
 * filter (Product/CropListing) or the search support Module 18 itself added
 * to Equipment/StayListing (see this module's report). This class's only
 * job is fanning the same term out to all four and shaping the combined
 * response — no new query logic of its own.
 */
@Service
public class SearchServiceImpl implements SearchService {

    /** "Quick results" cap per type — a full browse belongs on that type's own catalog page, not here. */
    private static final int RESULTS_PER_TYPE = 5;

    private final ProductService productService;
    private final CropListingService cropListingService;
    private final EquipmentService equipmentService;
    private final StayListingService stayListingService;

    public SearchServiceImpl(
            ProductService productService,
            CropListingService cropListingService,
            EquipmentService equipmentService,
            StayListingService stayListingService
    ) {
        this.productService = productService;
        this.cropListingService = cropListingService;
        this.equipmentService = equipmentService;
        this.stayListingService = stayListingService;
    }

    @Override
    public SearchResultsResponse search(String term) {
        if (term == null || term.isBlank()) {
            return new SearchResultsResponse(List.of(), 0, List.of(), 0, List.of(), 0, List.of(), 0, 0);
        }

        PaginatedResponse<ProductSummaryResponse> products = productService.listProducts(
                new ProductFilterRequest(null, null, null, null, term, 0, RESULTS_PER_TYPE));
        PaginatedResponse<CropListingSummaryResponse> cropListings = cropListingService.listCropListings(
                new CropListingFilterRequest(null, null, null, null, null, null, term, 0, RESULTS_PER_TYPE));
        PaginatedResponse<EquipmentSummaryResponse> equipment = equipmentService.listEquipment(null, term, 0, RESULTS_PER_TYPE);
        PaginatedResponse<StayListingSummaryResponse> stayListings = stayListingService.listStays(term, 0, RESULTS_PER_TYPE);

        long total = products.totalCount() + cropListings.totalCount() + equipment.totalCount() + stayListings.totalCount();

        return new SearchResultsResponse(
                products.items(), products.totalCount(),
                cropListings.items(), cropListings.totalCount(),
                equipment.items(), equipment.totalCount(),
                stayListings.items(), stayListings.totalCount(),
                total);
    }
}
