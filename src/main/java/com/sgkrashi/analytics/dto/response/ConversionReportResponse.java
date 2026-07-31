package com.sgkrashi.analytics.dto.response;

import java.util.List;

public record ConversionReportResponse(List<ConversionItem> items, ConversionItem overall) {

    public record ConversionItem(String moduleType, long totalInquiries, long convertedCount, double conversionRate) {
    }
}
