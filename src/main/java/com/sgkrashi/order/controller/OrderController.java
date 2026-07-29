package com.sgkrashi.order.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.order.dto.request.CheckoutRequest;
import com.sgkrashi.order.dto.response.OrderResponse;
import com.sgkrashi.order.dto.response.OrderSummaryResponse;
import com.sgkrashi.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.checkout(request), "Order placed"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PaginatedResponse<OrderSummaryResponse>>> listMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.listMyOrders(page, size), "Orders retrieved"));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderDetail(orderId), "Order retrieved"));
    }
}
