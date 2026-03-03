package com.articurated.controller;

import com.articurated.dto.OrderDto;
import com.articurated.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle management endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates an order in PENDING_PAYMENT state")
    public ResponseEntity<OrderDto.OrderResponse> createOrder(
            @Valid @RequestBody OrderDto.CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping
    @Operation(summary = "List all orders")
    public ResponseEntity<List<OrderDto.OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderDto.OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Transition order status",
            description = "Advances the order through its lifecycle. Valid transitions: PENDING_PAYMENT->PAID, PAID->PROCESSING_IN_WAREHOUSE, PROCESSING_IN_WAREHOUSE->SHIPPED, SHIPPED->DELIVERED, PENDING_PAYMENT/PAID->CANCELLED")
    public ResponseEntity<OrderDto.OrderResponse> transitionStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDto.TransitionRequest request) {
        return ResponseEntity.ok(orderService.transitionStatus(orderId, request));
    }

    @GetMapping("/{orderId}/history")
    @Operation(summary = "Get order status history", description = "Returns the audit trail of all status changes")
    public ResponseEntity<List<OrderDto.OrderStatusHistoryResponse>> getOrderHistory(
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderHistory(orderId));
    }
}
