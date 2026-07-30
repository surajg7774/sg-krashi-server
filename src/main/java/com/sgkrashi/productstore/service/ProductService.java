package com.sgkrashi.productstore.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.productstore.dto.request.ProductAdminRequest;
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

    /** Module 15 — Admin only. Nothing before this module ever created a Product through the service layer; all prior data was seeded via raw SQL. */
    ProductDetailResponse createProduct(ProductAdminRequest request);

    /** Module 15 — Admin only. {@code idOrSlug} not required to be currently active, so a soft-deleted product can be edited/reactivated. */
    ProductDetailResponse updateProduct(Long id, ProductAdminRequest request);

    /** Module 15 — Admin only. Soft delete (is_active = false) — never a hard delete, since historical orders/reviews reference this row. */
    void deactivateProduct(Long id);

    /** Module 15 — Admin only. Unlike {@link #listProducts}, does NOT filter by isActive — a deactivated product must stay visible/manageable in its own Admin table, or reactivating it would be impossible through the UI. */
    PaginatedResponse<ProductSummaryResponse> listProductsForAdmin(String search, int page, int size);

    /** Module 15 — Admin only. Unlike {@link #getProductDetail}, does NOT require the product to be active. */
    ProductDetailResponse getProductForAdmin(Long id);
}
