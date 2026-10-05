package com.sgkrashi.weather.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.weather.dto.response.DailyForecastPointResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyForecastApiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String payload(String time, String max, String min, String rain) {
        return """
                {"latitude":26.1,"longitude":91.7,"timezone":"Asia/Kolkata",
                 "daily_units":{"time":"iso8601"},
                 "daily":{"time":%s,"temperature_2m_max":%s,"temperature_2m_min":%s,"precipitation_sum":%s}}
                """.formatted(time, max, min, rain);
    }

    @Test
    void parsesEveryDayInOrder() {
        List<DailyForecastPointResponse> points = DailyForecastApiClient.parse(
                payload("[\"2026-10-05\",\"2026-10-06\",\"2026-10-07\"]", "[32.2,30.6,28.9]", "[25.5,23.5,22.3]", "[0.3,2.8,7.5]"),
                MAPPER);

        assertEquals(3, points.size());
        assertEquals(new DailyForecastPointResponse(LocalDate.of(2026, 10, 5), 32.2, 25.5, 0.3), points.get(0));
        assertEquals(new DailyForecastPointResponse(LocalDate.of(2026, 10, 7), 28.9, 22.3, 7.5), points.get(2));
    }

    @Test
    void dropsADayWithAMissingValueInsteadOfZeroFillingIt() {
        List<DailyForecastPointResponse> points = DailyForecastApiClient.parse(
                payload("[\"2026-10-05\",\"2026-10-06\",\"2026-10-07\"]", "[32.2,null,28.9]", "[25.5,23.5,22.3]", "[0.3,2.8,7.5]"),
                MAPPER);

        assertEquals(List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)), points.stream().map(DailyForecastPointResponse::date).toList());
    }

    @Test
    void rejectsAResponseWithNoUsableDay() {
        String allNull = payload("[\"2026-10-05\"]", "[null]", "[null]", "[null]");
        assertThrows(DailyForecastUnavailableException.class, () -> DailyForecastApiClient.parse(allNull, MAPPER));
    }

    @Test
    void rejectsMismatchedSeriesLengths() {
        String mismatched = payload("[\"2026-10-05\",\"2026-10-06\"]", "[32.2]", "[25.5,23.5]", "[0.3,2.8]");
        assertThrows(DailyForecastUnavailableException.class, () -> DailyForecastApiClient.parse(mismatched, MAPPER));
    }

    @Test
    void rejectsAMissingDailyBlockAndMalformedJson() {
        assertThrows(DailyForecastUnavailableException.class, () -> DailyForecastApiClient.parse("{\"latitude\":1}", MAPPER));
        assertThrows(DailyForecastUnavailableException.class, () -> DailyForecastApiClient.parse("not json", MAPPER));
    }
}
