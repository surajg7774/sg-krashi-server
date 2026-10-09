package com.sgkrashi.dairy.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.dairy.dto.DairyDtos.AdminDeliveryRow;
import com.sgkrashi.dairy.dto.DairyDtos.AdminSubscriptionResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryAreaRequest;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryAreaResponse;
import com.sgkrashi.dairy.dto.DairyDtos.DeliverySlotRequest;
import com.sgkrashi.dairy.dto.DairyDtos.DeliverySlotResponse;
import com.sgkrashi.dairy.dto.DairyDtos.MarkDeliveredRequest;
import com.sgkrashi.dairy.dto.DairyDtos.MarkFailedRequest;
import com.sgkrashi.dairy.entity.DairyDeliveryStatus;
import com.sgkrashi.dairy.entity.SubscriptionStatus;
import com.sgkrashi.dairy.service.AdminDairyService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Admin-only dairy management: delivery areas and slots, subscriptions, the daily delivery list. */
@RestController
@RequestMapping("/api/v1/admin/dairy")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminDairyController {

    private final AdminDairyService service;

    public AdminDairyController(AdminDairyService service) {
        this.service = service;
    }

    // ---- areas ----

    @GetMapping("/delivery-areas")
    public ResponseEntity<ApiResponse<List<DeliveryAreaResponse>>> areas() {
        return ResponseEntity.ok(ApiResponse.success(service.listAreas(), "Delivery areas retrieved"));
    }

    @PostMapping("/delivery-areas")
    public ResponseEntity<ApiResponse<DeliveryAreaResponse>> createArea(@Valid @RequestBody DeliveryAreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createArea(request), "Delivery area created"));
    }

    @PutMapping("/delivery-areas/{id}")
    public ResponseEntity<ApiResponse<DeliveryAreaResponse>> updateArea(@PathVariable Long id, @Valid @RequestBody DeliveryAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateArea(id, request), "Delivery area updated"));
    }

    @DeleteMapping("/delivery-areas/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateArea(@PathVariable Long id) {
        service.deactivateArea(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Delivery area deactivated"));
    }

    // ---- slots ----

    @GetMapping("/delivery-slots")
    public ResponseEntity<ApiResponse<List<DeliverySlotResponse>>> slots() {
        return ResponseEntity.ok(ApiResponse.success(service.listSlots(), "Delivery slots retrieved"));
    }

    @PostMapping("/delivery-slots")
    public ResponseEntity<ApiResponse<DeliverySlotResponse>> createSlot(@Valid @RequestBody DeliverySlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createSlot(request), "Delivery slot created"));
    }

    @PutMapping("/delivery-slots/{id}")
    public ResponseEntity<ApiResponse<DeliverySlotResponse>> updateSlot(@PathVariable Long id, @Valid @RequestBody DeliverySlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateSlot(id, request), "Delivery slot updated"));
    }

    @DeleteMapping("/delivery-slots/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateSlot(@PathVariable Long id) {
        service.deactivateSlot(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Delivery slot deactivated"));
    }

    // ---- subscriptions ----

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminSubscriptionResponse>>> subscriptions(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.listSubscriptions(status, userId, productId, page, size), "Subscriptions retrieved"));
    }

    // ---- daily delivery list ----

    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<List<AdminDeliveryRow>>> deliveries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long slotId,
            @RequestParam(required = false) DairyDeliveryStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.deliveriesForDate(date, slotId, status), "Deliveries retrieved"));
    }

    @GetMapping("/deliveries/export")
    public ResponseEntity<byte[]> exportDeliveries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long slotId,
            @RequestParam(required = false) DairyDeliveryStatus status
    ) {
        byte[] body = service.deliveriesCsv(date, slotId, status).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dairy-deliveries-" + date + ".csv\"")
                .body(body);
    }

    @PostMapping("/deliveries/{id}/delivered")
    public ResponseEntity<ApiResponse<AdminDeliveryRow>> delivered(@PathVariable Long id, @RequestBody(required = false) MarkDeliveredRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.markDelivered(id, request == null ? null : request.cashCollected()), "Delivery marked delivered"));
    }

    @PostMapping("/deliveries/{id}/failed")
    public ResponseEntity<ApiResponse<AdminDeliveryRow>> failed(@PathVariable Long id, @Valid @RequestBody(required = false) MarkFailedRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.markFailed(id, request == null ? null : request.reason()), "Delivery marked failed"));
    }
}
