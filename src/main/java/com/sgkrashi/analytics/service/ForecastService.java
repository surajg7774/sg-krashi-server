package com.sgkrashi.analytics.service;

import com.sgkrashi.analytics.dto.response.RevenueForecastResponse;
import com.sgkrashi.analytics.dto.response.StockRiskResponse;

public interface ForecastService {

    /** Linear regression over recent daily revenue, projected {@code days} forward — see {@code ForecastServiceImpl}'s Javadoc for why. */
    RevenueForecastResponse getRevenueForecast(int days);

    /** Current stock vs. recent average daily sales rate, per active product — flags anything under the at-risk threshold. */
    StockRiskResponse getStockRisk();
}
