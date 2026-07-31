package com.sgkrashi.search.dto.response;

import com.sgkrashi.cropmarketplace.dto.response.CropListingSummaryResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;
import com.sgkrashi.farmstay.dto.response.StayListingSummaryResponse;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;

import java.util.List;

/**
 * {@code items} per type are capped ("quick results", top 5) — {@code
 * TotalCount} per type is the REAL total matching count from that type's own
 * paginated query, so the frontend's "See all N results" link can show the
 * true number even though only a handful are returned here.
 */
public record SearchResultsResponse(
        List<ProductSummaryResponse> products,
        long productsTotalCount,
        List<CropListingSummaryResponse> cropListings,
        long cropListingsTotalCount,
        List<EquipmentSummaryResponse> equipment,
        long equipmentTotalCount,
        List<StayListingSummaryResponse> stayListings,
        long stayListingsTotalCount,
        long totalCount
) {
}
