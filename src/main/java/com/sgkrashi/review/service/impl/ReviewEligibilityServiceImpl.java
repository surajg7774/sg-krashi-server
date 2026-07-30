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
 * <h2>The two gaps this module found, and how they're handled</h2>
 *
 * <p><b>Orders (Product/CropListing):</b> Module 6/7's {@code OrderStatus}
 * enum is {@code PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED} — there is no
 * {@code DELIVERED} state, or any fulfillment/shipping tracking at all.
 * {@code CONFIRMED} is the terminal successful state as this codebase
 * currently stands. Rather than inventing a {@code DELIVERED} constant that
 * nothing would ever set (a half-finished status nobody transitions to),
 * this service treats a {@code CONFIRMED} order as the review-eligible
 * state. A real "delivered" distinction belongs to a future fulfillment-
 * tracking module, not a speculative addition here — user-facing copy for
 * this reflects that ("once your order is complete", not "delivered").
 *
 * <p><b>Bookings (Equipment/Stay):</b> {@code BookingStatus.COMPLETED} exists
 * as an enum constant and is defensively checked in {@code
 * BookingServiceImpl.cancelBooking}, but nothing anywhere ever sets a
 * booking's status to it — no scheduled job, no manual transition. Per this
 * module's own brief, the pragmatic fix is: a {@code CONFIRMED} booking whose
 * {@code endDate} has already passed is treated as equivalent to {@code
 * COMPLETED} for review-eligibility purposes only. This does not retroactively
 * fix {@code BookingStatus.COMPLETED} being otherwise unreachable — that's a
 * gap for a future module (e.g. a stay/rental completion job) to close.
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
                    userId, OrderStatus.CONFIRMED, toItemType(targetType), targetId);
            return candidates.stream()
                    .filter(item -> !reviewRepository.existsByOrderItemId(item.getId()))
                    .findFirst()
                    .map(item -> EligibilityResponse.eligible(item.getId(), null))
                    .orElseGet(() -> candidates.isEmpty()
                            ? EligibilityResponse.notEligible("You can review this once your order for it is complete.")
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
                    orderItemId, userId, OrderStatus.CONFIRMED, toItemType(targetType), targetId);
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
