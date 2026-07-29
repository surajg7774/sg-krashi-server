package com.sgkrashi.productstore.service;

import com.sgkrashi.productstore.dto.response.ProductCategoryResponse;

import java.util.List;

public interface ProductCategoryService {

    /** Top-level categories with their children nested, not a flat list. */
    List<ProductCategoryResponse> getCategoryTree();
}
