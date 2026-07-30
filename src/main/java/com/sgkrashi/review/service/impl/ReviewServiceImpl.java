package com.sgkrashi.review.service.impl;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.cropmarketplace.entity.CropListing;
import com.sgkrashi.cropmarketplace.repository.CropListingRepository;
import com.sgkrashi.equipmentrental.entity.Equipment;
import com.sgkrashi.equipmentrental.repository.EquipmentRepository;
import com.sgkrashi.farmstay.entity.StayListing;
import com.sgkrashi.farmstay.repository.StayListingRepository;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import com.sgkrashi.review.dto.request.CreateReviewRequest;
import com.sgkrashi.review.dto.response.EligibilityResponse;
import com.sgkrashi.review.dto.response.RatingSummaryResponse;
import com.sgkrashi.review.dto.response.ReviewListResponse;
import com.sgkrashi.review.dto.response.ReviewResponse;
import com.sgkrashi.review.entity.Review;
import com.sgkrashi.review.entity.ReviewTargetType;
import com.sgkrashi.review.mapper.ReviewMapper;
import com.sgkrashi.review.repository.ReviewRepository;
import com.sgkrashi.review.service.ReviewEligibilityService;
import com.sgkrashi.review.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CropListingRepository cropListingRepository;
    private final EquipmentRepository equipmentRepository;
    private final StayListingRepository stayListingRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ReviewEligibilityService reviewEligibilityService;
    private final ReviewMapper reviewMapper;

    public ReviewServiceImpl(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            CropListingRepository cropListingRepository,
            EquipmentRepository equipmentRepository,
            StayListingRepository stayListingRepository,
            CurrentUserProvider currentUserProvider,
            ReviewEligibilityService reviewEligibilityService,
            ReviewMapper reviewMapper
    ) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.cropListingRepository = cropListingRepository;
        this.equipmentRepository = equipmentRepository;
        this.stayListingRepository = stayListingRepository;
        this.currentUserProvider = currentUserProvider;
        this.reviewEligibilityService = reviewEligibilityService;
        this.reviewMapper = reviewMapper;
    }

    @Override
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {
        boolean isOrderBacked = request.targetType() == ReviewTargetType.PRODUCT
                || request.targetType() == ReviewTargetType.CROP_LISTING;

        if (isOrderBacked && (request.orderItemId() == null || request.bookingId() != null)) {
            throw new BusinessRuleException("A " + request.targetType() + " review must reference an orderItemId, not a bookingId");
        }
        if (!isOrderBacked && (request.bookingId() == null || request.orderItemId() != null)) {
            throw new BusinessRuleException("A " + request.targetType() + " review must reference a bookingId, not an orderItemId");
        }

        reviewEligibilityService.assertEligible(
                request.targetType(), request.targetId(), request.orderItemId(), request.bookingId());

        Long userId = currentUserProvider.getCurrentUserId();

        Review review = new Review();
        review.setUserId(userId);
        review.setTargetType(request.targetType());
        review.setTargetId(request.targetId());
        review.setOrderItemId(request.orderItemId());
        review.setBookingId(request.bookingId());
        review.setRating(request.rating());
        review.setComment(request.comment());

        Review saved = reviewRepository.save(review);

        updateTargetRatingAggregate(request.targetType(), request.targetId());

        User reviewer = currentUserProvider.getCurrentUser();
        return reviewMapper.toResponse(saved, reviewer.getName());
    }

    @Override
    public ReviewListResponse listReviews(ReviewTargetType targetType, Long targetId, int page, int size) {
        Page<Review> reviewPage = reviewRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                targetType, targetId, PageRequest.of(page, size));

        List<Long> userIds = reviewPage.getContent().stream().map(Review::getUserId).distinct().toList();
        Map<Long, String> namesByUserId = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        List<ReviewResponse> items = reviewPage.getContent().stream()
                .map(review -> reviewMapper.toResponse(review, namesByUserId.get(review.getUserId())))
                .toList();

        RatingSummaryResponse ratingSummary = buildRatingSummary(targetType, targetId);

        return ReviewListResponse.of(PaginatedResponse.of(items, reviewPage), ratingSummary);
    }

    @Override
    public EligibilityResponse checkEligibility(ReviewTargetType targetType, Long targetId) {
        return reviewEligibilityService.checkEligibility(targetType, targetId);
    }

    private RatingSummaryResponse buildRatingSummary(ReviewTargetType targetType, Long targetId) {
        Double avg = reviewRepository.averageRating(targetType, targetId);
        long count = reviewRepository.countByTargetTypeAndTargetId(targetType, targetId);
        BigDecimal avgRating = avg != null ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP) : null;
        return new RatingSummaryResponse(avgRating, (int) count);
    }

    private void updateTargetRatingAggregate(ReviewTargetType targetType, Long targetId) {
        Double avg = reviewRepository.averageRating(targetType, targetId);
        long count = reviewRepository.countByTargetTypeAndTargetId(targetType, targetId);
        BigDecimal avgRating = avg != null ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP) : null;

        switch (targetType) {
            case PRODUCT -> {
                Product product = productRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
                product.setAvgRating(avgRating);
                product.setReviewCount((int) count);
                productRepository.save(product);
            }
            case CROP_LISTING -> {
                CropListing listing = cropListingRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Crop listing not found"));
                listing.setAvgRating(avgRating);
                listing.setReviewCount((int) count);
                cropListingRepository.save(listing);
            }
            case EQUIPMENT -> {
                Equipment equipment = equipmentRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
                equipment.setAvgRating(avgRating);
                equipment.setReviewCount((int) count);
                equipmentRepository.save(equipment);
            }
            case STAY -> {
                StayListing stay = stayListingRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Stay listing not found"));
                stay.setAvgRating(avgRating);
                stay.setReviewCount((int) count);
                stayListingRepository.save(stay);
            }
        }
    }
}
