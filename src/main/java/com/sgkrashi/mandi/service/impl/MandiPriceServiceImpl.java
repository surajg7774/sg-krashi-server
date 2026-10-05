package com.sgkrashi.mandi.service.impl;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.mandi.client.MandiPriceApiClient;
import com.sgkrashi.mandi.dto.response.MandiFilterOptionsResponse;
import com.sgkrashi.mandi.dto.response.MandiPriceResponse;
import com.sgkrashi.mandi.dto.response.MandiSyncMetaResponse;
import com.sgkrashi.mandi.dto.response.MandiTrendPointResponse;
import com.sgkrashi.mandi.entity.MandiPrice;
import com.sgkrashi.mandi.repository.MandiPriceRepository;
import com.sgkrashi.mandi.service.MandiPriceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class MandiPriceServiceImpl implements MandiPriceService {

    private final MandiPriceRepository mandiPriceRepository;
    private final MandiPriceApiClient mandiPriceApiClient;

    public MandiPriceServiceImpl(MandiPriceRepository mandiPriceRepository, MandiPriceApiClient mandiPriceApiClient) {
        this.mandiPriceRepository = mandiPriceRepository;
        this.mandiPriceApiClient = mandiPriceApiClient;
    }

    @Override
    public PaginatedResponse<MandiPriceResponse> search(String commodity, String state, String market, int page, int size) {
        Page<MandiPrice> result = mandiPriceRepository.search(blankToNull(commodity), blankToNull(state), blankToNull(market), PageRequest.of(page, size));
        List<MandiPriceResponse> items = result.getContent().stream().map(this::toResponse).toList();
        return PaginatedResponse.of(items, result);
    }

    @Override
    public MandiFilterOptionsResponse getFilterOptions(String state) {
        return new MandiFilterOptionsResponse(
                mandiPriceRepository.findDistinctCommodities(),
                mandiPriceRepository.findDistinctStates(),
                mandiPriceRepository.findDistinctMarkets(blankToNull(state)));
    }

    @Override
    public List<MandiTrendPointResponse> getTrend(String commodity, String state, String market) {
        // One point per day: rows are unique per (commodity, market, date), so with
        // no market filter a day has one row per market — averaged here rather than
        // returned raw, otherwise a chart zig-zags between markets' prices. With a
        // market filter each day is a single row, so the average is just that row.
        Map<LocalDate, List<BigDecimal>> modalPricesByDate = mandiPriceRepository
                .findTrend(commodity, blankToNull(state), blankToNull(market)).stream()
                .collect(Collectors.groupingBy(
                        MandiPrice::getPriceDate, TreeMap::new,
                        Collectors.mapping(MandiPrice::getModalPrice, Collectors.toList())));
        return modalPricesByDate.entrySet().stream()
                .map(e -> new MandiTrendPointResponse(e.getKey(), average(e.getValue())))
                .toList();
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    @Override
    public MandiSyncMetaResponse getSyncMeta() {
        Instant lastSyncedAt = mandiPriceRepository.findLastSyncedAt().orElse(null);
        return new MandiSyncMetaResponse(lastSyncedAt, mandiPriceRepository.count(), mandiPriceApiClient.isConfigured());
    }

    private MandiPriceResponse toResponse(MandiPrice price) {
        return new MandiPriceResponse(
                price.getId(), price.getCommodity(), price.getMarketName(), price.getState(), price.getDistrict(),
                price.getMinPrice(), price.getMaxPrice(), price.getModalPrice(), price.getPriceDate());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
