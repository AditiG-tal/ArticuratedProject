package com.articurated.jobs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class Messages {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceGenerationMessage implements Serializable {
        private UUID orderId;
        private String orderNumber;
        private String customerName;
        private String customerEmail;
        private BigDecimal totalAmount;
        private List<OrderItemDetail> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDetail implements Serializable {
        private String productName;
        private String productSku;
        private Integer quantity;
        private BigDecimal unitPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundProcessingMessage implements Serializable {
        private UUID returnRequestId;
        private UUID orderId;
        private String orderNumber;
        private String customerEmail;
        private BigDecimal refundAmount;
    }
}
