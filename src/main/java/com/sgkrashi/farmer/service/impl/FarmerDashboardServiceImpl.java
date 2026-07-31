package com.sgkrashi.farmer.service.impl;

import com.sgkrashi.cropmarketplace.repository.CropListingRepository;
import com.sgkrashi.farmer.dto.response.FarmerDashboardSummaryResponse;
import com.sgkrashi.farmer.service.FarmerDashboardService;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.repository.OrderItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Order stats here count CONFIRMED and REFUNDED orders (a REFUNDED order was
 * confirmed-then-refunded, still a real historical sale) — same "was
 * genuinely confirmed at some point" rule Module 19's top-listings queries
 * use, not a fresh convention invented for this module.
 */
@Service
public class FarmerDashboardServiceImpl implements FarmerDashboardService {

    private static final List<OrderStatus> COUNTED_ORDER_STATUSES = List.of(OrderStatus.CONFIRMED, OrderStatus.REFUNDED);

    private final CropListingRepository cropListingRepository;
    private final OrderItemRepository orderItemRepository;

    public FarmerDashboardServiceImpl(CropListingRepository cropListingRepository, OrderItemRepository orderItemRepository) {
        this.cropListingRepository = cropListingRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public FarmerDashboardSummaryResponse getSummary(Long farmerId) {
        long totalListings = cropListingRepository.countByFarmerId(farmerId);
        long activeListings = cropListingRepository.countByFarmerIdAndIsActiveTrue(farmerId);
        long ordersContainingListings = orderItemRepository.countDistinctOrdersByFarmerId(farmerId, COUNTED_ORDER_STATUSES);
        long unitsSold = orderItemRepository.sumQuantityByFarmerId(farmerId, COUNTED_ORDER_STATUSES);
        return new FarmerDashboardSummaryResponse(totalListings, activeListings, ordersContainingListings, unitsSold);
    }
}
