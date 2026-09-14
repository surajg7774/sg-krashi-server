package com.sgkrashi.scheme.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.scheme.dto.request.SchemeRequest;
import com.sgkrashi.scheme.dto.response.SchemeResponse;
import com.sgkrashi.scheme.service.SchemeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Same open-to-plain-ADMIN convention as {@code AdminContentBlockController} — reference-content editing, not a Super-Admin-only capability. */
@RestController
@RequestMapping("/api/v1/admin/schemes")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminSchemeController {

    private final SchemeService schemeService;

    public AdminSchemeController(SchemeService schemeService) {
        this.schemeService = schemeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SchemeResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(schemeService.listForAdmin(), "Schemes retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SchemeResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(schemeService.getForAdmin(id), "Scheme retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SchemeResponse>> create(@Valid @RequestBody SchemeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(schemeService.create(request), "Scheme created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SchemeResponse>> update(@PathVariable Long id, @Valid @RequestBody SchemeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(schemeService.update(id, request), "Scheme updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        schemeService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Scheme deactivated"));
    }
}
