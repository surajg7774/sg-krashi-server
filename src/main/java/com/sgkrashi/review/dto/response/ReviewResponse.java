package com.sgkrashi.review.dto.response;

import com.sgkrashi.review.entity.ReviewTargetType;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        ReviewTargetType targetType,
        Long targetId,
        String reviewerName,
        int rating,
        String comment,
        Instant createdAt
) {
}
