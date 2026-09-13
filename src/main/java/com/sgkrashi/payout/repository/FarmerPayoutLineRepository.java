package com.sgkrashi.payout.repository;

import com.sgkrashi.payout.entity.FarmerPayoutLine;
import com.sgkrashi.payout.entity.PayoutLineType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FarmerPayoutLineRepository extends JpaRepository<FarmerPayoutLine, Long> {

    /** Eagerly fetches the order item and its order — needed to show order number/item name on a line, and avoids a lazy-session pitfall (see {@code OrderItemRepository.findByOrderId}'s Javadoc for the same reasoning). */
    @EntityGraph(attributePaths = {"orderItem", "orderItem.order"})
    List<FarmerPayoutLine> findByPayoutIdOrderByIdAsc(Long payoutId);

    boolean existsByOrderItemIdAndLineType(Long orderItemId, PayoutLineType lineType);

    /** Used to read the original EARNING line's amounts when building its CLAWBACK counterpart — see {@code PayoutService.handleOrderRefunded}. */
    Optional<FarmerPayoutLine> findByOrderItemIdAndLineType(Long orderItemId, PayoutLineType lineType);
}
