package com.sgkrashi.booking.dto.response;

import java.util.List;

public record AvailabilityResponse(boolean available, List<DateRangeResponse> conflictingRanges) {
}
