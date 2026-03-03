package com.articurated.dto;

import com.articurated.enums.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class OrderDto {

    @Data
    public static class CreateOrderRequest {
        @NotBlank(message = "Customer name is required")
        private String customerName;

        @NotBlank(message = "Customer email is required")
        @Email(message = "Invalid email format")
        private String customerEmail;

        @NotEmpty(message = "Order must have at least one item")
        @Valid
        private List<OrderItemRequest> items;
    }

    @Data
    public static class OrderItemRequest {
        @NotBlank(message = "Product name is required")
        private String productName;

        @NotBlank(message = "Product SKU is required")
        private String productSku;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
        private BigDecimal unitPrice;
    }

    @Data
    public static class TransitionRequest {
        @NotNull(message = "Target status is required")
        private OrderStatus targetStatus;

        private String notes;
        private String changedBy = "SYSTEM";
    }

    @Data
    public static class OrderResponse {
        private UUID id;
        private String orderNumber;
        private String customerName;
        private String customerEmail;
        private BigDecimal totalAmount;
        private OrderStatus status;
        private String invoicePath;
        private List<OrderItemResponse> items;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class OrderItemResponse {
        private UUID id;
        private String productName;
        private String productSku;
        private Integer quantity;
        private BigDecimal unitPrice;
    }

    @Data
    public static class OrderStatusHistoryResponse {
        private UUID id;
        private OrderStatus fromStatus;
        private OrderStatus toStatus;
        private String notes;
        private String changedBy;
        private LocalDateTime changedAt;
    }
}
