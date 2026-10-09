package com.sgkrashi.dairy.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.dairy.dto.DairyDtos.CreateSubscriptionRequest;
import com.sgkrashi.dairy.dto.DairyDtos.DeliveryHistoryItem;
import com.sgkrashi.dairy.dto.DairyDtos.PauseRequest;
import com.sgkrashi.dairy.dto.DairyDtos.SkipRequest;
import com.sgkrashi.dairy.dto.DairyDtos.SubscriptionResponse;
import com.sgkrashi.dairy.service.DairySubscriptionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The signed-in customer's own dairy subscriptions. Everything here needs a login (the default for this path). */
@RestController
@RequestMapping("/api/v1/dairy/subscriptions")
public class DairySubscriptionController {

    private final DairySubscriptionService service;

    public DairySubscriptionController(DairySubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(request), "Subscription created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.listMine(), "Subscriptions retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getMine(id), "Subscription retrieved"));
    }

    @GetMapping("/{id}/deliveries")
    public ResponseEntity<ApiResponse<PaginatedResponse<DeliveryHistoryItem>>> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.history(id, page, size), "Deliveries retrieved"));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> pause(@PathVariable Long id, @Valid @RequestBody PauseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.pause(id, request), "Subscription paused"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.resume(id), "Subscription resumed"));
    }

    @PostMapping("/{id}/skip")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> skip(@PathVariable Long id, @Valid @RequestBody SkipRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.skip(id, request.date()), "Delivery skipped"));
    }

    @DeleteMapping("/{id}/skip")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> unskip(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.unskip(id, date), "Skip removed"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.cancel(id), "Subscription cancelled"));
    }
}
