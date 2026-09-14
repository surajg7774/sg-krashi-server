package com.sgkrashi.scheme.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.scheme.dto.response.SchemeResponse;
import com.sgkrashi.scheme.entity.SchemeCategory;
import com.sgkrashi.scheme.service.SchemeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated — reference info only, links out to the real government portals for the actual application flow. */
@RestController
@RequestMapping("/api/v1/schemes")
public class SchemeController {

    private final SchemeService schemeService;

    public SchemeController(SchemeService schemeService) {
        this.schemeService = schemeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SchemeResponse>>> list(@RequestParam(required = false) SchemeCategory category) {
        return ResponseEntity.ok(ApiResponse.success(schemeService.listPublic(category), "Schemes retrieved"));
    }
}
