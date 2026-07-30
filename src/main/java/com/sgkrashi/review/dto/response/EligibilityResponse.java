package com.sgkrashi.review.dto.response;

/**
 * {@code orderItemId}/{@code bookingId} are only populated when {@code
 * eligible} is true — the specific completed transaction found, echoed back
 * so the frontend's review submission can reference it without the client
 * ever having to know or guess its own order/booking IDs.
 */
public record EligibilityResponse(boolean eligible, String reason, Long orderItemId, Long bookingId) {

    public static EligibilityResponse eligible(Long orderItemId, Long bookingId) {
        return new EligibilityResponse(true, null, orderItemId, bookingId);
    }

    public static EligibilityResponse notEligible(String reason) {
        return new EligibilityResponse(false, reason, null, null);
    }
}
