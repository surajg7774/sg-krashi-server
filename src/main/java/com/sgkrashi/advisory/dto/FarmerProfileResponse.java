package com.sgkrashi.advisory.dto;

import java.math.BigDecimal;

public record FarmerProfileResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        String placeName,
        boolean weatherAdvisoryOptIn
) {
}
