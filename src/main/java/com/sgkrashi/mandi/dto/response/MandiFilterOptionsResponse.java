package com.sgkrashi.mandi.dto.response;

import java.util.List;

/** Distinct values to populate the price tracker's filter dropdowns. */
public record MandiFilterOptionsResponse(List<String> commodities, List<String> states, List<String> markets) {
}
