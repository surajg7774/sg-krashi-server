package com.sgkrashi.recommendation.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.recommendation.dto.response.RecommendationResponse;
import com.sgkrashi.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /similar} and {@code /frequently-bought-with} are public (see
 * {@code SecurityConfig}'s PUBLIC_GET_ENDPOINTS) — a first-time visitor gets
 * recommendations with no login required. {@code /for-you} is NOT in that
 * list, so it falls through to the default {@code anyRequest().authenticated()}
 * rule, since it reads the caller's own order history.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/similar")
    public ResponseEntity<ApiResponse<RecommendationResponse>> similar(
            @RequestParam String targetType,
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        var result = recommendationService.getSimilarItems(targetType, targetId, limit);
        return ResponseEntity.ok(ApiResponse.success(result, "Similar items retrieved"));
    }

    @GetMapping("/frequently-bought-with")
    public ResponseEntity<ApiResponse<RecommendationResponse>> frequentlyBoughtWith(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        var result = recommendationService.getFrequentlyBoughtWith(productId, limit);
        return ResponseEntity.ok(ApiResponse.success(result, "Frequently bought with retrieved"));
    }

    @GetMapping("/for-you")
    public ResponseEntity<ApiResponse<RecommendationResponse>> forYou(
            @RequestParam(defaultValue = "8") int limit
    ) {
        var result = recommendationService.getForYou(limit);
        return ResponseEntity.ok(ApiResponse.success(result, "Recommendations retrieved"));
    }
}
