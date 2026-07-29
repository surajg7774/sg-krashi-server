package com.sgkrashi.cropmarketplace.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.cropmarketplace.dto.response.CropCategoryResponse;
import com.sgkrashi.cropmarketplace.service.CropCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated — same as the product-category endpoint. */
@RestController
@RequestMapping("/api/v1/crop-categories")
public class CropCategoryController {

    private final CropCategoryService cropCategoryService;

    public CropCategoryController(CropCategoryService cropCategoryService) {
        this.cropCategoryService = cropCategoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CropCategoryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(cropCategoryService.listCategories(), "Crop categories retrieved"));
    }
}
