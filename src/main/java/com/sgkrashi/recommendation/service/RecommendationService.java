package com.sgkrashi.recommendation.service;

import com.sgkrashi.recommendation.dto.response.RecommendationResponse;

public interface RecommendationService {

    /** Content-based — same category, similar price band, ranked by rating. Works with no user history at all. */
    RecommendationResponse getSimilarItems(String targetType, Long targetId, int limit);

    /** Item-based collaborative filtering — co-occurrence across completed orders ("customers who bought this also bought"). Products only (see Javadoc on the repository query). */
    RecommendationResponse getFrequentlyBoughtWith(Long productId, int limit);

    /** Authenticated — seeded by the current user's own purchase-history categories; degrades to platform-wide top-rated for a customer with no order history. */
    RecommendationResponse getForYou(int limit);
}
