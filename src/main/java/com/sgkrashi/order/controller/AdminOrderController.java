package com.sgkrashi.order.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.order.dto.request.AdminOrderStatusUpdateRequest;
import com.sgkrashi.order.dto.response.AdminOrderDetailResponse;
import com.sgkrashi.order.dto.response.AdminOrderSummaryResponse;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.service.OrderService;
import com.sgkrashi.payment.dto.response.RefundResultResponse;
import com.sgkrashi.payment.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Platform-wide Order visibility and management (Module 16) — not scoped to one Admin, every Admin/Super Admin sees every order. */
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminOrderController {

    /** Explicit IST anchor for day-boundary date filters, same convention as {@code BookingServiceImpl.BOOKING_ZONE}. */
    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");

    private final OrderService orderService;
    private final RefundService refundService;

    public AdminOrderController(OrderService orderService, RefundService refundService) {
        this.orderService = orderService;
        this.refundService = refundService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminOrderSummaryResponse>>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Instant from = dateFrom == null ? null : dateFrom.atStartOfDay(ADMIN_ZONE).toInstant();
        Instant to = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ADMIN_ZONE).toInstant();
        return ResponseEntity.ok(ApiResponse.success(
                orderService.listOrdersForAdmin(status, userId, from, to, page, size), "Orders retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminOrderDetailResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderDetailForAdmin(id), "Order retrieved"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminOrderDetailResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminOrderStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.updateOrderStatus(id, request.status(), request.adminNotes()), "Order status updated"));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<RefundResultResponse>> refund(@PathVariable Long id) {
        RefundResultResponse result = refundService.refundOrder(id);
        String message = result.alreadyRefunded() ? "This order was already refunded" : "Refund processed";
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }
}
