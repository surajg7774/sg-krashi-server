package com.sgkrashi.weather.dto.response;

import java.time.LocalDate;

/** One day of Open-Meteo's daily forecast, in the location's own timezone — a point on the weather sparkline. */
public record DailyForecastPointResponse(LocalDate date, double tempMaxC, double tempMinC, double rainMm) {
}
