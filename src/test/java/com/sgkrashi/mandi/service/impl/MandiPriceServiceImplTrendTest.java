package com.sgkrashi.mandi.service.impl;

import com.sgkrashi.mandi.client.MandiPriceApiClient;
import com.sgkrashi.mandi.dto.response.MandiTrendPointResponse;
import com.sgkrashi.mandi.entity.MandiPrice;
import com.sgkrashi.mandi.repository.MandiPriceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure-Java fixtures, built in memory for this test only — nothing here touches a database or seed data. */
class MandiPriceServiceImplTrendTest {

    private final MandiPriceRepository repository = mock(MandiPriceRepository.class);
    private final MandiPriceServiceImpl service = new MandiPriceServiceImpl(repository, mock(MandiPriceApiClient.class));

    private static MandiPrice row(String market, LocalDate date, String modal) {
        MandiPrice p = new MandiPrice();
        p.setCommodity("Wheat");
        p.setMarketName(market);
        p.setPriceDate(date);
        p.setModalPrice(new BigDecimal(modal));
        return p;
    }

    @Test
    void averagesAcrossMarketsToOnePointPerDay() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 2);
        when(repository.findTrend("Wheat", null, null)).thenReturn(List.of(
                row("Indore", d1, "2000.00"), row("Khandwa", d1, "2100.00"), row("Bhopal", d1, "2200.00"),
                row("Indore", d2, "2050.00"), row("Khandwa", d2, "2051.00")));

        List<MandiTrendPointResponse> trend = service.getTrend("Wheat", null, null);

        assertEquals(List.of(
                new MandiTrendPointResponse(d1, new BigDecimal("2100.00")),
                new MandiTrendPointResponse(d2, new BigDecimal("2050.50"))), trend);
    }

    @Test
    void aSingleMarketPassesThroughAsItsOwnPrice() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        when(repository.findTrend("Wheat", null, "Indore")).thenReturn(List.of(row("Indore", d1, "2000.50")));

        assertEquals(List.of(new MandiTrendPointResponse(d1, new BigDecimal("2000.50"))),
                service.getTrend("Wheat", null, "Indore"));
    }

    @Test
    void returnsPointsInAscendingDateOrderWhateverOrderTheRowsComeIn() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 2);
        LocalDate d3 = LocalDate.of(2026, 10, 3);
        when(repository.findTrend("Wheat", null, null)).thenReturn(List.of(
                row("Indore", d3, "3.00"), row("Indore", d1, "1.00"), row("Indore", d2, "2.00")));

        assertEquals(List.of(d1, d2, d3), service.getTrend("Wheat", null, null).stream().map(MandiTrendPointResponse::priceDate).toList());
    }

    @Test
    void roundsTheAverageHalfUpToTwoDecimals() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        when(repository.findTrend("Wheat", null, null)).thenReturn(List.of(
                row("A", d1, "100.00"), row("B", d1, "100.00"), row("C", d1, "101.00")));

        assertEquals(new BigDecimal("100.33"), service.getTrend("Wheat", null, null).get(0).modalPrice());
    }

    @Test
    void blankStateAndMarketAreTreatedAsNoFilter() {
        when(repository.findTrend("Wheat", null, null)).thenReturn(List.of());

        assertEquals(List.of(), service.getTrend("Wheat", "  ", ""));
        verify(repository).findTrend("Wheat", null, null);
    }
}
