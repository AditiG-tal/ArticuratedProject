package com.articurated.jobs;

import com.articurated.config.RabbitMQConfig;
import com.articurated.entity.Order;
import com.articurated.repository.OrderRepository;
import com.articurated.service.PdfInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class InvoiceGenerationConsumer {

    private final PdfInvoiceService pdfInvoiceService;
    private final OrderRepository orderRepository;

    @RabbitListener(queues = RabbitMQConfig.INVOICE_GENERATION_QUEUE)
    @Transactional
    public void processInvoiceGeneration(Messages.InvoiceGenerationMessage message) {
        log.info("Received invoice generation job for order: {}", message.getOrderNumber());

        try {
            // Generate PDF
            String invoicePath = pdfInvoiceService.generateInvoice(message);

            // Update order with invoice path
            orderRepository.findById(message.getOrderId()).ifPresent(order -> {
                order.setInvoicePath(invoicePath);
                orderRepository.save(order);
            });

            // Simulate email
            pdfInvoiceService.simulateSendEmail(
                    message.getCustomerEmail(),
                    message.getOrderNumber(),
                    invoicePath
            );

            log.info("Invoice generation completed for order: {}", message.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to generate invoice for order: {}", message.getOrderNumber(), e);
            throw e; // Re-throw to trigger DLQ
        }
    }
}
