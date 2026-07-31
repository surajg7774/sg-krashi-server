package com.sgkrashi.analytics.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Ranked by revenue (not unit/night count) — the more business-meaningful ordering per this module's own brief. */
public record TopListingsResponse(List<TopListingItem> items) {

    public record TopListingItem(Long id, String name, long unitsSold, BigDecimal revenue) {
    }
}
