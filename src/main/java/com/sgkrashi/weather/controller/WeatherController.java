package com.sgkrashi.weather.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.weather.dto.response.GeocodingResultResponse;
import com.sgkrashi.weather.dto.response.PublicWeatherResponse;
import com.sgkrashi.weather.service.PublicWeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated — current weather and location search for the homepage widget and the /weather page. */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final PublicWeatherService publicWeatherService;

    public WeatherController(PublicWeatherService publicWeatherService) {
        this.publicWeatherService = publicWeatherService;
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<PublicWeatherResponse>> current(
            @RequestParam double lat,
            @RequestParam double lon
    ) {
        return ResponseEntity.ok(ApiResponse.success(publicWeatherService.getWeather(lat, lon), "Weather retrieved"));
    }

    @GetMapping("/geocode")
    public ResponseEntity<ApiResponse<List<GeocodingResultResponse>>> geocode(@RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(publicWeatherService.searchLocations(query), "Locations retrieved"));
    }
}
