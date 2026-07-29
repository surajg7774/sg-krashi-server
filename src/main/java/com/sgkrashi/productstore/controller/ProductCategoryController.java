package com.sgkrashi.productstore.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.productstore.dto.response.ProductCategoryResponse;
import com.sgkrashi.productstore.service.ProductCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated — same as the product endpoints. */
@RestController
@RequestMapping("/api/v1/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    public ProductCategoryController(ProductCategoryService productCategoryService) {
        this.productCategoryService = productCategoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductCategoryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.getCategoryTree(), "Categories retrieved"));
    }
}
