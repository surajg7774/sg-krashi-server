package com.sgkrashi.review.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.booking.entity.BookableType;
import com.sgkrashi.booking.entity.Booking;
import com.sgkrashi.booking.repository.BookingRepository;
import com.sgkrashi.common.entity.ItemType;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.order.entity.OrderItem;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.repository.OrderItemRepository;
import com.sgkrashi.review.dto.response.EligibilityResponse;
import com.sgkrashi.review.entity.ReviewTargetType;
import com.sgkrashi.review.repository.ReviewRepository;
import com.sgkrashi.review.service.ReviewEligibilityService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * <h2>Real DELIVERED/COMPLETED states, not the old proxies</h2>
 *
 * <p><b>Orders (Product/CropListing):</b> used to treat {@code CONFIRMED} as
 * review-eligible, since {@code OrderStatus} had no {@code DELIVERED} state
 * at all. Now that {@code DELIVERED} is real and Admin-settable, review
 * eligibility requires it — a customer can review a product/crop-listing
 * purchase once it's genuinely marked delivered, not merely paid-for.
 *
 * <p><b>Bookings (Equipment/Stay):</b> {@code BookingStatus.COMPLETED} is now
 * actually reachable — {@code BookingCompletionJob} transitions a
 * {@code CONFIRMED} booking to it once {@code endDate} has passed. This
 * service now checks {@code COMPLETED} as the primary signal. The old
 * "{@code CONFIRMED} and {@code endDate} passed" proxy is deliberately KEPT,
 * but demoted to a documented fallback for the ~24h window between a
 * booking's {@code endDate} passing and the next daily job run — see {@code
 * BookingRepository.findEligibleBookingById}'s Javadoc for the full
 * reasoning. It is not a second, competing signal: once the job runs, the
 * booking is COMPLETED and the fallback clause simply stops matching it.
 */
@Service
public class ReviewEligibilityServiceImpl implements ReviewEligibilityService {

    private final OrderItemRepository orderItemRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final CurrentUserProvider currentUserProvider;

    public ReviewEligibilityServiceImpl(
            OrderItemRepository orderItemRepository,
            BookingRepository bookingRepository,
            ReviewRepository reviewRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.orderItemRepository = orderItemRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public EligibilityResponse checkEligibility(ReviewTargetType targetType, Long targetId) {
        Long userId = currentUserProvider.getCurrentUserId();

        if (isOrderBacked(targetType)) {
            List<OrderItem> candidates = orderItemRepository.findEligibleForReview(
                    userId, OrderStatus.DELIVERED, toItemType(targetType), targetId);
            return candidates.stream()
                    .filter(item -> !reviewRepository.existsByOrderItemId(item.getId()))
                    .findFirst()
                    .map(item -> EligibilityResponse.eligible(item.getId(), null))
                    .orElseGet(() -> candidates.isEmpty()
                            ? EligibilityResponse.notEligible("You can review this once your order has been delivered.")
                            : EligibilityResponse.notEligible("You've already reviewed this."));
        }

        List<Booking> candidates = bookingRepository.findEligibleForReview(
                userId, toBookableType(targetType), targetId, LocalDate.now());
        return candidates.stream()
                .filter(booking -> !reviewRepository.existsByBookingId(booking.getId()))
                .findFirst()
                .map(booking -> EligibilityResponse.eligible(null, booking.getId()))
                .orElseGet(() -> candidates.isEmpty()
                        ? EligibilityResponse.notEligible("You can review this once your stay/booking is complete.")
                        : EligibilityResponse.notEligible("You've already reviewed this."));
    }

    @Override
    public void assertEligible(ReviewTargetType targetType, Long targetId, Long orderItemId, Long bookingId) {
        Long userId = currentUserProvider.getCurrentUserId();

        if (isOrderBacked(targetType)) {
            if (orderItemId == null) {
                throw new BusinessRuleException("orderItemId is required to review a " + targetType);
            }
            Optional<OrderItem> item = orderItemRepository.findEligibleOrderItemById(
                    orderItemId, userId, OrderStatus.DELIVERED, toItemType(targetType), targetId);
            if (item.isEmpty()) {
                throw new BusinessRuleException("You are not eligible to review this item");
            }
            if (reviewRepository.existsByOrderItemId(orderItemId)) {
                throw new BusinessRuleException("You've already reviewed this order item");
            }
            return;
        }

        if (bookingId == null) {
            throw new BusinessRuleException("bookingId is required to review a " + targetType);
        }
        Optional<Booking> booking = bookingRepository.findEligibleBookingById(
                bookingId, userId, toBookableType(targetType), targetId, LocalDate.now());
        if (booking.isEmpty()) {
            throw new BusinessRuleException("You are not eligible to review this item");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new BusinessRuleException("You've already reviewed this booking");
        }
    }

    private boolean isOrderBacked(ReviewTargetType targetType) {
        return targetType == ReviewTargetType.PRODUCT || targetType == ReviewTargetType.CROP_LISTING;
    }

    private ItemType toItemType(ReviewTargetType targetType) {
        return targetType == ReviewTargetType.PRODUCT ? ItemType.PRODUCT : ItemType.CROP_LISTING;
    }

    private BookableType toBookableType(ReviewTargetType targetType) {
        return targetType == ReviewTargetType.EQUIPMENT ? BookableType.EQUIPMENT : BookableType.STAY;
    }
}
