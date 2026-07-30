package com.sgkrashi.review.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.review.dto.request.CreateReviewRequest;
import com.sgkrashi.review.dto.response.EligibilityResponse;
import com.sgkrashi.review.dto.response.ReviewListResponse;
import com.sgkrashi.review.dto.response.ReviewResponse;
import com.sgkrashi.review.entity.ReviewTargetType;
import com.sgkrashi.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reviewService.createReview(request), "Review submitted"));
    }

    /** Public — anyone can read reviews for a target, same as browsing a catalog. */
    @GetMapping
    public ResponseEntity<ApiResponse<ReviewListResponse>> list(
            @RequestParam ReviewTargetType targetType,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.listReviews(targetType, targetId, page, size), "Reviews retrieved"));
    }

    /** Authenticated — used to decide whether to show the "Leave a Review" CTA. */
    @GetMapping("/eligibility")
    public ResponseEntity<ApiResponse<EligibilityResponse>> eligibility(
            @RequestParam ReviewTargetType targetType,
            @RequestParam Long targetId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.checkEligibility(targetType, targetId), "Eligibility checked"));
    }
}
