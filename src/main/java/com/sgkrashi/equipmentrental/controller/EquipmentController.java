package com.sgkrashi.equipmentrental.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentDetailResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;
import com.sgkrashi.equipmentrental.service.EquipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated — same as the product/crop-listing catalog endpoints. */
@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<EquipmentSummaryResponse>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = equipmentService.listEquipment(category, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Equipment retrieved"));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> categories() {
        return ResponseEntity.ok(ApiResponse.success(equipmentService.listCategories(), "Categories retrieved"));
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<EquipmentDetailResponse>> getOne(@PathVariable String idOrSlug) {
        return ResponseEntity.ok(ApiResponse.success(equipmentService.getEquipmentDetail(idOrSlug), "Equipment retrieved"));
    }
}
