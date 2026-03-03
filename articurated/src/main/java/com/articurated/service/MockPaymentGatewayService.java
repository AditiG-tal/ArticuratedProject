package com.articurated.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock payment gateway service that simulates API calls to an external payment processor.
 * In production this would call a real payment gateway (Stripe, PayPal, etc.)
 */
@Service
@Slf4j
public class MockPaymentGatewayService {

    @Value("${app.payment-gateway.url:http://mock-payment-gateway:8081}")
    private String gatewayUrl;

    /**
     * Simulates a refund API call to the payment gateway.
     * Returns a mock transaction ID.
     */
    public String processRefund(UUID orderId, BigDecimal amount, String customerEmail) {
        log.info("=== [PAYMENT GATEWAY] Processing refund ===");
        log.info("Gateway URL: {}/api/refunds", gatewayUrl);
        log.info("Order ID: {}", orderId);
        log.info("Refund Amount: ${}", amount);
        log.info("Customer Email: {}", customerEmail);

        // Simulate network latency
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate mock transaction ID
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("=== [PAYMENT GATEWAY] Refund successful. Transaction ID: {} ===", transactionId);
        return transactionId;
    }
}
