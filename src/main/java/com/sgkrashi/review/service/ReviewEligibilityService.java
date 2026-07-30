package com.sgkrashi.review.service;

import com.sgkrashi.review.dto.response.EligibilityResponse;
import com.sgkrashi.review.entity.ReviewTargetType;

public interface ReviewEligibilityService {

    /**
     * Searches the current user's own completed transactions for this
     * target and returns the first one not yet reviewed, or a
     * not-eligible result with a reason. Used both to decide whether to
     * show the "Leave a Review" CTA and to discover which orderItemId/
     * bookingId a subsequent {@code POST /reviews} should reference.
     */
    EligibilityResponse checkEligibility(ReviewTargetType targetType, Long targetId);

    /**
     * Re-verifies a specific transaction the caller claims backs a review —
     * ownership, status, target match, and not-already-reviewed — never
     * trusts the client-supplied ID as a bare claim. Throws {@code
     * BusinessRuleException} if ineligible.
     */
    void assertEligible(ReviewTargetType targetType, Long targetId, Long orderItemId, Long bookingId);
}
