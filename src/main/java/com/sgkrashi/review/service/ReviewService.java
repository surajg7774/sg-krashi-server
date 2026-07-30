package com.sgkrashi.review.service;

import com.sgkrashi.review.dto.request.CreateReviewRequest;
import com.sgkrashi.review.dto.response.EligibilityResponse;
import com.sgkrashi.review.dto.response.ReviewListResponse;
import com.sgkrashi.review.dto.response.ReviewResponse;
import com.sgkrashi.review.entity.ReviewTargetType;

public interface ReviewService {

    ReviewResponse createReview(CreateReviewRequest request);

    ReviewListResponse listReviews(ReviewTargetType targetType, Long targetId, int page, int size);

    EligibilityResponse checkEligibility(ReviewTargetType targetType, Long targetId);
}
