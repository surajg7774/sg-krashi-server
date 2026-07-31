package com.sgkrashi.search.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.search.dto.response.SearchResultsResponse;
import com.sgkrashi.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated — search is a Guest-permitted action, consistent with browsing any catalog. */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultsResponse>> search(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(searchService.search(q), "Search results retrieved"));
    }
}
