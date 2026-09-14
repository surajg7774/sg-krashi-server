package com.sgkrashi.mandi.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MandiTrendPointResponse(LocalDate priceDate, BigDecimal modalPrice) {
}
