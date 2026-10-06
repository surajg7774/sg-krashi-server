package com.sgkrashi.insights.service;

import com.sgkrashi.insights.dto.response.InsightsResponses.ActivityResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.BookingsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.FulfilmentResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.OrdersResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SignupsResponse;
import com.sgkrashi.insights.dto.response.InsightsResponses.SnapshotResponse;
import com.sgkrashi.insights.util.Granularity;

import java.time.LocalDate;

/** Read-only business insights for admins, computed on demand from existing tables. Dates are India-time calendar days; {@code to} is inclusive. */
public interface AdminInsightsService {

    SignupsResponse signups(LocalDate from, LocalDate to, Granularity groupBy);

    OrdersResponse orders(LocalDate from, LocalDate to, Granularity groupBy);

    FulfilmentResponse fulfilment(LocalDate from, LocalDate to);

    BookingsResponse bookings(LocalDate from, LocalDate to, Granularity groupBy);

    ActivityResponse activity(LocalDate from, LocalDate to, Granularity groupBy);

    SnapshotResponse snapshot();
}
