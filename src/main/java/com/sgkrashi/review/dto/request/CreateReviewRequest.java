package com.sgkrashi.review.dto.request;

import com.sgkrashi.review.entity.ReviewTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Exactly one of {@link #orderItemId}/{@link #bookingId} must be supplied,
 * matching {@link #targetType} (PRODUCT/CROP_LISTING → orderItemId,
 * EQUIPMENT/STAY → bookingId) — validated in the service layer, not here,
 * since it's a cross-field rule. Both IDs are re-verified server-side against
 * the caller's own transactions ({@code ReviewEligibilityService}) — never
 * trusted as a bare claim.
 */
public record CreateReviewRequest(
        @NotNull(message = "Target type is required")
        ReviewTargetType targetType,

        @NotNull(message = "Target id is required")
        Long targetId,

        Long orderItemId,

        Long bookingId,

        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        int rating,

        @NotBlank(message = "Comment is required")
        @Size(max = 2000, message = "Comment must be at most 2000 characters")
        String comment
) {
}
