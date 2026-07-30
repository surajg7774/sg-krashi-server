package com.sgkrashi.equipmentrental.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.equipmentrental.dto.request.EquipmentAdminRequest;
import com.sgkrashi.equipmentrental.dto.response.EquipmentDetailResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;
import com.sgkrashi.equipmentrental.service.EquipmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only write surface for Equipment — see {@code AdminProductController}'s Javadoc for why this is a sibling in the domain package. */
@RestController
@RequestMapping("/api/v1/admin/equipment")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminEquipmentController {

    private final EquipmentService equipmentService;

    public AdminEquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<EquipmentSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(equipmentService.listEquipmentForAdmin(search, page, size), "Equipment retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EquipmentDetailResponse>> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(equipmentService.getEquipmentForAdmin(id), "Equipment retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EquipmentDetailResponse>> create(@Valid @RequestBody EquipmentAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(equipmentService.createEquipment(request), "Equipment created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EquipmentDetailResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentAdminRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(equipmentService.updateEquipment(id, request), "Equipment updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        equipmentService.deactivateEquipment(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Equipment deactivated"));
    }
}
