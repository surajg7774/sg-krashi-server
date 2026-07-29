package com.sgkrashi.productstore.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.productstore.dto.request.ProductFilterRequest;
import com.sgkrashi.productstore.dto.response.ProductDetailResponse;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;

public interface ProductService {

    PaginatedResponse<ProductSummaryResponse> listProducts(ProductFilterRequest filter);

    /**
     * Accepts either a numeric product ID or its slug.
     *
     * @throws com.sgkrashi.common.exception.ResourceNotFoundException if no active product matches
     */
    ProductDetailResponse getProductDetail(String idOrSlug);
}
