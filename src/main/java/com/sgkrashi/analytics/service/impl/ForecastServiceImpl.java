package com.sgkrashi.analytics.service.impl;

import com.sgkrashi.analytics.dto.response.RevenueForecastResponse;
import com.sgkrashi.analytics.dto.response.RevenueReportResponse;
import com.sgkrashi.analytics.dto.response.StockRiskResponse;
import com.sgkrashi.analytics.repository.AnalyticsQueryRepository;
import com.sgkrashi.analytics.service.AnalyticsService;
import com.sgkrashi.analytics.service.ForecastService;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Predictive Analytics (Section 3 of the spec) — two deliberately simple,
 * explainable calculations over already-aggregated data, not a trained
 * model or a complex time-series pipeline:
 * <ul>
 *   <li>{@link #getRevenueForecast} — <b>linear regression</b> (ordinary
 *   least squares) over the trailing {@value #REGRESSION_WINDOW_DAYS} days
 *   of daily revenue, chosen over a plain moving average specifically
 *   because it captures trend <i>direction</i> (a business trending up vs.
 *   down projects very differently) — a moving average would just flat-line
 *   the recent average forward regardless of direction. The math is a
 *   handful of sums, no library.</li>
 *   <li>{@link #getStockRisk} — current stock divided by a recent average
 *   daily sales rate, per active product.</li>
 * </ul>
 */
@Service
public class ForecastServiceImpl implements ForecastService {

    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String TECHNIQUE_LINEAR_REGRESSION = "LINEAR_REGRESSION";
    // Trailing window the trend line is fit against — long enough to smooth
    // day-to-day noise, short enough that an old shift in the business
    // doesn't keep dragging on today's projection.
    private static final int REGRESSION_WINDOW_DAYS = 60;
    private static final int DEFAULT_FORECAST_DAYS = 30;
    private static final int STOCK_RISK_WINDOW_DAYS = 30;
    private static final int STOCK_RISK_THRESHOLD_DAYS = 7;

    private final AnalyticsService analyticsService;
    private final AnalyticsQueryRepository analyticsQueryRepository;
    private final ProductRepository productRepository;

    public ForecastServiceImpl(
            AnalyticsService analyticsService,
            AnalyticsQueryRepository analyticsQueryRepository,
            ProductRepository productRepository
    ) {
        this.analyticsService = analyticsService;
        this.analyticsQueryRepository = analyticsQueryRepository;
        this.productRepository = productRepository;
    }

    @Override
    public RevenueForecastResponse getRevenueForecast(int days) {
        int effectiveDays = days > 0 ? days : DEFAULT_FORECAST_DAYS;
        Instant to = Instant.now();
        Instant from = to.minus(REGRESSION_WINDOW_DAYS, ChronoUnit.DAYS);
        List<RevenueReportResponse.RevenuePoint> historical = analyticsService.getRevenueReport(from, to, "day").points();

        if (historical.size() < 2) {
            // Not enough history to fit a meaningful trend line — flat-line
            // the forecast at the last known day's total (or zero) rather
            // than dividing by near-zero variance.
            BigDecimal flat = historical.isEmpty() ? BigDecimal.ZERO : historical.get(historical.size() - 1).total();
            List<RevenueForecastResponse.ForecastPoint> flatForecast =
                    buildForecastPoints(historical, effectiveDays, x -> flat);
            return new RevenueForecastResponse(historical, flatForecast, TECHNIQUE_LINEAR_REGRESSION);
        }

        int n = historical.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double y = historical.get(i).total().doubleValue();
            sumX += i;
            sumY += y;
            sumXY += (double) i * y;
            sumXX += (double) i * i;
        }
        double denominator = n * sumXX - sumX * sumX;
        double slope = denominator == 0 ? 0 : (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        List<RevenueForecastResponse.ForecastPoint> forecast = buildForecastPoints(historical, effectiveDays, x -> {
            // Revenue can't go negative — a steeply declining trend line
            // shouldn't project into negative territory.
            double projected = Math.max(0, intercept + slope * x);
            return BigDecimal.valueOf(projected).setScale(2, RoundingMode.HALF_UP);
        });

        return new RevenueForecastResponse(historical, forecast, TECHNIQUE_LINEAR_REGRESSION);
    }

    private List<RevenueForecastResponse.ForecastPoint> buildForecastPoints(
            List<RevenueReportResponse.RevenuePoint> historical, int days, IntFunction<BigDecimal> valueAtIndex) {
        LocalDate nextDate = historical.isEmpty()
                ? LocalDate.now(ADMIN_ZONE)
                : LocalDate.parse(historical.get(historical.size() - 1).bucket()).plusDays(1);
        int startIndex = historical.size();

        List<RevenueForecastResponse.ForecastPoint> forecast = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            BigDecimal value = valueAtIndex.apply(startIndex + i);
            forecast.add(new RevenueForecastResponse.ForecastPoint(nextDate.plusDays(i).toString(), value));
        }
        return forecast;
    }

    @Override
    public StockRiskResponse getStockRisk() {
        Instant to = Instant.now();
        Instant from = to.minus(STOCK_RISK_WINDOW_DAYS, ChronoUnit.DAYS);

        Map<Long, Long> soldQtyByProductId = new HashMap<>();
        for (Object[] row : analyticsQueryRepository.findRecentProductSalesQuantity(from, to)) {
            soldQtyByProductId.put(toLong(row[0]), toLong(row[1]));
        }

        List<StockRiskResponse.StockRiskItem> items = productRepository.findByIsActiveTrue().stream()
                .map(product -> buildStockRiskItem(product, soldQtyByProductId.getOrDefault(product.getId(), 0L)))
                .sorted(Comparator.comparingDouble(
                        item -> item.daysRemaining() == null ? Double.MAX_VALUE : item.daysRemaining()))
                .toList();

        return new StockRiskResponse(items);
    }

    private StockRiskResponse.StockRiskItem buildStockRiskItem(Product product, long soldQtyInWindow) {
        double avgDailySales = soldQtyInWindow / (double) STOCK_RISK_WINDOW_DAYS;
        Double daysRemaining = avgDailySales > 0 ? product.getStockQty() / avgDailySales : null;
        boolean isAtRisk = daysRemaining != null && daysRemaining < STOCK_RISK_THRESHOLD_DAYS;
        return new StockRiskResponse.StockRiskItem(
                product.getId(),
                product.getName(),
                product.getStockQty(),
                Math.round(avgDailySales * 100) / 100.0,
                daysRemaining,
                isAtRisk);
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
