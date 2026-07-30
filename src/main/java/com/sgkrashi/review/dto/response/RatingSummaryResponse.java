package com.sgkrashi.review.dto.response;

import java.math.BigDecimal;

public record RatingSummaryResponse(BigDecimal avgRating, int reviewCount) {
}
