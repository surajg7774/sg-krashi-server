package com.sgkrashi.dairy.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DairyConfigResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DairyProductResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryOptionsResponse;
import com.sgkrashi.dairy.service.DairyStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only data for the Dairy page. No personal data; see SecurityConfig. */
@RestController
@RequestMapping("/api/v1/dairy-store")
public class DairyStoreController {

    private final DairyStoreService service;

    public DairyStoreController(DairyStoreService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<DairyConfigResponse>> config() {
        return ResponseEntity.ok(ApiResponse.success(service.config(), "Dairy configuration retrieved"));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PaginatedResponse<DairyProductResponse>>> products(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.listProducts(page, size), "Dairy products retrieved"));
    }

    @GetMapping("/product-ids")
    public ResponseEntity<ApiResponse<java.util.List<Long>>> productIds() {
        return ResponseEntity.ok(ApiResponse.success(service.dairyProductIds(), "Dairy product ids retrieved"));
    }

    @GetMapping("/delivery-options")
    public ResponseEntity<ApiResponse<DeliveryOptionsResponse>> deliveryOptions(@RequestParam(required = false) String pincode) {
        return ResponseEntity.ok(ApiResponse.success(service.deliveryOptions(pincode), "Delivery options retrieved"));
    }
}
