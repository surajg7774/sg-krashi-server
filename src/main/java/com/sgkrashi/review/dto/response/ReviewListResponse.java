package com.sgkrashi.review.dto.response;

import com.sgkrashi.common.dto.PaginatedResponse;

import java.util.List;

/**
 * Same shape as {@code PaginatedResponse<ReviewResponse>} plus {@link
 * #ratingSummary} — bundled together so the frontend's review list and its
 * rating summary badge refresh from a single query/invalidation after a new
 * review, rather than needing two separate in-sync fetches.
 */
public record ReviewListResponse(
        List<ReviewResponse> items,
        int page,
        int pageSize,
        long totalCount,
        int totalPages,
        RatingSummaryResponse ratingSummary
) {
    public static ReviewListResponse of(PaginatedResponse<ReviewResponse> paginated, RatingSummaryResponse ratingSummary) {
        return new ReviewListResponse(
                paginated.items(), paginated.page(), paginated.pageSize(), paginated.totalCount(), paginated.totalPages(), ratingSummary);
    }
}
