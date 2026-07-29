package com.sgkrashi.productstore.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.productstore.dto.request.ProductFilterRequest;
import com.sgkrashi.productstore.dto.response.ProductDetailResponse;
import com.sgkrashi.productstore.dto.response.ProductSummaryResponse;
import com.sgkrashi.productstore.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Public, unauthenticated — Guests browse the catalog freely. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<ProductSummaryResponse>>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean organicOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ProductFilterRequest filter = new ProductFilterRequest(
                categoryId, minPrice, maxPrice, organicOnly, search, page, size);
        var result = productService.listProducts(filter);
        return ResponseEntity.ok(ApiResponse.success(result, "Products retrieved"));
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getOne(@PathVariable String idOrSlug) {
        ProductDetailResponse response = productService.getProductDetail(idOrSlug);
        return ResponseEntity.ok(ApiResponse.success(response, "Product retrieved"));
    }
}
