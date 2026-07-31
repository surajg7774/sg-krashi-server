package com.sgkrashi.search.service;

import com.sgkrashi.search.dto.response.SearchResultsResponse;

public interface SearchService {

    /**
     * Queries the four existing catalog services (Modules 5/7/8/9) for the
     * top 5 matches each, by name, active items only. A blank/null term
     * returns all-empty results without querying anything.
     */
    SearchResultsResponse search(String term);
}
