package com.sgkrashi.review.mapper;

import com.sgkrashi.review.dto.response.ReviewResponse;
import com.sgkrashi.review.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review, String reviewerName) {
        return new ReviewResponse(
                review.getId(),
                review.getTargetType(),
                review.getTargetId(),
                reviewerName,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
