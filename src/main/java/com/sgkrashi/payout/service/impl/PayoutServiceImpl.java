package com.sgkrashi.payout.service.impl;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.order.entity.OrderItem;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.repository.OrderItemRepository;
import com.sgkrashi.payout.dto.response.AdminPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.AdminPayoutSummaryResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutDetailResponse;
import com.sgkrashi.payout.dto.response.FarmerPayoutSummaryResponse;
import com.sgkrashi.payout.dto.response.PendingPayoutSummaryResponse;
import com.sgkrashi.payout.entity.FarmerPayout;
import com.sgkrashi.payout.entity.FarmerPayoutLine;
import com.sgkrashi.payout.entity.PayoutLineType;
import com.sgkrashi.payout.entity.PayoutStatus;
import com.sgkrashi.payout.mapper.PayoutMapper;
import com.sgkrashi.payout.repository.FarmerPayoutLineRepository;
import com.sgkrashi.payout.repository.FarmerPayoutRepository;
import com.sgkrashi.payout.service.PayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PayoutServiceImpl implements PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutServiceImpl.class);
    private static final ZoneId PAYOUT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");

    private final FarmerPayoutRepository farmerPayoutRepository;
    private final FarmerPayoutLineRepository farmerPayoutLineRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final PayoutMapper payoutMapper;

    public PayoutServiceImpl(
            FarmerPayoutRepository farmerPayoutRepository,
            FarmerPayoutLineRepository farmerPayoutLineRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository,
            PayoutMapper payoutMapper
    ) {
        this.farmerPayoutRepository = farmerPayoutRepository;
        this.farmerPayoutLineRepository = farmerPayoutLineRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.payoutMapper = payoutMapper;
    }

    /** A line's gross amount split into (gross, commission, net) at the flat platform rate. */
    private record Split(BigDecimal gross, BigDecimal commission, BigDecimal net) {
    }

    @Override
    public List<Long> findFarmerIdsPendingSweep() {
        return orderItemRepository.findFarmerIdsWithUnbatchedDeliveredItems(OrderStatus.DELIVERED, PayoutLineType.EARNING);
    }

    @Override
    @Transactional
    public int sweepFarmer(Long farmerId) {
        List<OrderItem> items = orderItemRepository.findUnbatchedDeliveredItemsForFarmer(
                farmerId, OrderStatus.DELIVERED, PayoutLineType.EARNING);
        if (items.isEmpty()) {
            return 0;
        }

        FarmerPayout payout = getOrCreateOpenBatch(farmerId);
        for (OrderItem item : items) {
            Split split = computeSplit(item.getLineTotal());
            FarmerPayoutLine line = new FarmerPayoutLine();
            line.setPayout(payout);
            line.setOrderItem(item);
            line.setLineType(PayoutLineType.EARNING);
            line.setGrossAmount(split.gross());
            line.setCommissionAmount(split.commission());
            line.setNetAmount(split.net());
            farmerPayoutLineRepository.save(line);
        }
        recomputeTotals(payout.getId());
        return items.size();
    }

    /**
     * Called from {@code NotificationEventListener}'s payout sibling after
     * an Order's refund transaction has already committed. If the order's
     * crop-listing item(s) were never linked into a payout, there is
     * nothing to reverse — they simply stay excluded from every future
     * sweep, which {@code o.status = DELIVERED} (now false) already
     * guarantees. If an item WAS already linked, the original line is left
     * untouched and a new negative CLAWBACK line is added to the farmer's
     * current open batch (creating one if none exists) instead.
     */
    @Override
    @Transactional
    public void handleOrderRefunded(Long orderId) {
        log.info("handleOrderRefunded: called for orderId={}", orderId);
        List<OrderItem> cropListingItems = orderItemRepository.findAllByOrderId(orderId).stream()
                .filter(item -> item.getItemType() == ItemType.CROP_LISTING)
                .toList();
        log.info("handleOrderRefunded: orderId={} has {} crop-listing item(s): {}",
                orderId, cropListingItems.size(), cropListingItems.stream().map(OrderItem::getId).toList());

        for (OrderItem item : cropListingItems) {
            clawBackIfAlreadyLinked(item);
        }
    }

    private void clawBackIfAlreadyLinked(OrderItem item) {
        try {
            Optional<FarmerPayoutLine> earningLine = farmerPayoutLineRepository.findByOrderItemIdAndLineType(item.getId(), PayoutLineType.EARNING);
            log.info("clawBackIfAlreadyLinked: orderItemId={} earningLine present={}", item.getId(), earningLine.isPresent());
            if (earningLine.isEmpty()) {
                return;
            }
            // Defensive idempotency guard — RefundServiceImpl already guarantees
            // markRefunded (and therefore this event) fires at most once per
            // refund, but this mirrors that class's own "defensive second layer,
            // not the primary guarantee" style.
            boolean alreadyClawedBack = farmerPayoutLineRepository.existsByOrderItemIdAndLineType(item.getId(), PayoutLineType.CLAWBACK);
            log.info("clawBackIfAlreadyLinked: orderItemId={} alreadyClawedBack={}", item.getId(), alreadyClawedBack);
            if (alreadyClawedBack) {
                return;
            }

            FarmerPayoutLine original = earningLine.get();
            log.info("clawBackIfAlreadyLinked: orderItemId={} original line id={} gross={}", item.getId(), original.getId(), original.getGrossAmount());
            Long farmerId = item.getCropListing().getFarmerId();
            log.info("clawBackIfAlreadyLinked: orderItemId={} farmerId={}", item.getId(), farmerId);
            FarmerPayout openBatch = getOrCreateOpenBatch(farmerId);
            log.info("clawBackIfAlreadyLinked: orderItemId={} openBatch id={} status={}", item.getId(), openBatch.getId(), openBatch.getStatus());

            FarmerPayoutLine clawback = new FarmerPayoutLine();
            clawback.setPayout(openBatch);
            clawback.setOrderItem(item);
            clawback.setLineType(PayoutLineType.CLAWBACK);
            clawback.setGrossAmount(original.getGrossAmount().negate());
            clawback.setCommissionAmount(original.getCommissionAmount().negate());
            clawback.setNetAmount(original.getNetAmount().negate());
            FarmerPayoutLine savedClawback = farmerPayoutLineRepository.saveAndFlush(clawback);
            log.info("clawBackIfAlreadyLinked: orderItemId={} saved clawback line id={}", item.getId(), savedClawback.getId());
            long countAfterFlush = farmerPayoutLineRepository.count();
            log.info("clawBackIfAlreadyLinked: orderItemId={} total farmer_payout_lines rows after flush={}", item.getId(), countAfterFlush);

            recomputeTotals(openBatch.getId());
            log.info("clawBackIfAlreadyLinked: orderItemId={} recomputeTotals done", item.getId());
        } catch (RuntimeException ex) {
            log.error("clawBackIfAlreadyLinked: FAILED for orderItemId={}", item.getId(), ex);
            throw ex;
        }
    }

    @Override
    public PaginatedResponse<FarmerPayoutSummaryResponse> listOwnPayouts(Long farmerId, int page, int size) {
        Page<FarmerPayout> payoutsPage = farmerPayoutRepository.findByFarmerIdOrderByCreatedAtDesc(farmerId, PageRequest.of(page, size));
        List<FarmerPayoutSummaryResponse> items = payoutsPage.getContent().stream().map(payoutMapper::toFarmerSummary).toList();
        return PaginatedResponse.of(items, payoutsPage);
    }

    @Override
    public FarmerPayoutDetailResponse getOwnPayoutDetail(Long farmerId, Long payoutId) {
        FarmerPayout payout = farmerPayoutRepository.findByIdAndFarmerId(payoutId, farmerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
        List<FarmerPayoutLine> lines = farmerPayoutLineRepository.findByPayoutIdOrderByIdAsc(payoutId);
        return payoutMapper.toFarmerDetail(payout, lines);
    }

    @Override
    public PendingPayoutSummaryResponse getPendingAccrued(Long farmerId) {
        List<OrderItem> items = orderItemRepository.findUnbatchedDeliveredItemsForFarmer(
                farmerId, OrderStatus.DELIVERED, PayoutLineType.EARNING);

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (OrderItem item : items) {
            Split split = computeSplit(item.getLineTotal());
            gross = gross.add(split.gross());
            commission = commission.add(split.commission());
            net = net.add(split.net());
        }
        return new PendingPayoutSummaryResponse(gross, commission, net, items.size());
    }

    @Override
    public PaginatedResponse<AdminPayoutSummaryResponse> listForAdmin(PayoutStatus status, int page, int size) {
        Page<FarmerPayout> payoutsPage = status != null
                ? farmerPayoutRepository.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(page, size))
                : farmerPayoutRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));

        Map<Long, User> usersById = usersByFarmerIds(payoutsPage.getContent().stream().map(FarmerPayout::getFarmerId).distinct().toList());
        List<AdminPayoutSummaryResponse> items = payoutsPage.getContent().stream()
                .map(payout -> {
                    User user = usersById.get(payout.getFarmerId());
                    return payoutMapper.toAdminSummary(payout, user != null ? user.getName() : "Unknown", user != null ? user.getEmail() : null);
                })
                .toList();
        return PaginatedResponse.of(items, payoutsPage);
    }

    @Override
    public AdminPayoutDetailResponse getDetailForAdmin(Long payoutId) {
        FarmerPayout payout = getPayoutOrThrow(payoutId);
        User user = userRepository.findById(payout.getFarmerId()).orElse(null);
        List<FarmerPayoutLine> lines = farmerPayoutLineRepository.findByPayoutIdOrderByIdAsc(payoutId);
        return payoutMapper.toAdminDetail(payout, user != null ? user.getName() : "Unknown", user != null ? user.getEmail() : null, lines);
    }

    @Override
    @Transactional
    public AdminPayoutDetailResponse approve(Long payoutId, Long adminId) {
        FarmerPayout payout = getPayoutOrThrow(payoutId);
        if (payout.getStatus() != PayoutStatus.APPROVED) {
            if (payout.getStatus() != PayoutStatus.BATCHED) {
                throw new BusinessRuleException("Only a batched payout can be approved (current status: " + payout.getStatus() + ")");
            }
            payout.setStatus(PayoutStatus.APPROVED);
            payout.setApprovedBy(adminId);
            payout.setApprovedAt(Instant.now());
            farmerPayoutRepository.save(payout);
        }
        return getDetailForAdmin(payoutId);
    }

    @Override
    @Transactional
    public AdminPayoutDetailResponse markPaid(Long payoutId) {
        FarmerPayout payout = getPayoutOrThrow(payoutId);
        if (payout.getStatus() != PayoutStatus.PAID) {
            if (payout.getStatus() != PayoutStatus.APPROVED) {
                throw new BusinessRuleException("Only an approved payout can be marked paid (current status: " + payout.getStatus() + ")");
            }
            payout.setStatus(PayoutStatus.PAID);
            payout.setPaidAt(Instant.now());
            farmerPayoutRepository.save(payout);
        }
        return getDetailForAdmin(payoutId);
    }

    /**
     * At most one BATCHED payout should ever exist per farmer at a time —
     * both the weekly sweep and a refund clawback reuse it rather than
     * creating a second one, extending {@code cycleEndDate} to today on
     * reuse. {@code cycleStartDate} is only ever set once, when the batch
     * is first opened.
     */
    private FarmerPayout getOrCreateOpenBatch(Long farmerId) {
        LocalDate today = LocalDate.now(PAYOUT_ZONE);
        Optional<FarmerPayout> existing = farmerPayoutRepository.findFirstByFarmerIdAndStatusOrderByIdDesc(farmerId, PayoutStatus.BATCHED);
        if (existing.isPresent()) {
            FarmerPayout payout = existing.get();
            payout.setCycleEndDate(today);
            return farmerPayoutRepository.save(payout);
        }

        FarmerPayout payout = new FarmerPayout();
        payout.setFarmerId(farmerId);
        payout.setCycleStartDate(today);
        payout.setCycleEndDate(today);
        payout.setGrossAmount(BigDecimal.ZERO);
        payout.setCommissionAmount(BigDecimal.ZERO);
        payout.setNetAmount(BigDecimal.ZERO);
        payout.setStatus(PayoutStatus.BATCHED);
        return farmerPayoutRepository.save(payout);
    }

    /** The payout's own gross/commission/net are always this sum, never accumulated independently — see {@code FarmerPayout}'s Javadoc. */
    private void recomputeTotals(Long payoutId) {
        List<FarmerPayoutLine> lines = farmerPayoutLineRepository.findByPayoutIdOrderByIdAsc(payoutId);
        BigDecimal gross = lines.stream().map(FarmerPayoutLine::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = lines.stream().map(FarmerPayoutLine::getCommissionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = lines.stream().map(FarmerPayoutLine::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        FarmerPayout payout = getPayoutOrThrow(payoutId);
        payout.setGrossAmount(gross);
        payout.setCommissionAmount(commission);
        payout.setNetAmount(net);
        farmerPayoutRepository.save(payout);
    }

    private Split computeSplit(BigDecimal gross) {
        BigDecimal commission = gross.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(commission);
        return new Split(gross, commission, net);
    }

    private FarmerPayout getPayoutOrThrow(Long payoutId) {
        return farmerPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
    }

    private Map<Long, User> usersByFarmerIds(List<Long> farmerIds) {
        return userRepository.findAllById(farmerIds).stream().collect(Collectors.toMap(User::getId, u -> u));
    }
}
