package com.sgkrashi.weather.dto.response;

/** One candidate location from a place-name/pincode search — {@code admin1} is the state/region (may be null for a country-level match). */
public record GeocodingResultResponse(
        String name,
        String admin1,
        String country,
        double latitude,
        double longitude
) {
}
