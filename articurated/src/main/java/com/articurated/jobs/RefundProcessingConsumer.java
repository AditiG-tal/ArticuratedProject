package com.articurated.jobs;

import com.articurated.config.RabbitMQConfig;
import com.articurated.repository.ReturnRequestRepository;
import com.articurated.service.MockPaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefundProcessingConsumer {

    private final MockPaymentGatewayService paymentGatewayService;
    private final ReturnRequestRepository returnRequestRepository;

    @RabbitListener(queues = RabbitMQConfig.REFUND_PROCESSING_QUEUE)
    @Transactional
    public void processRefund(Messages.RefundProcessingMessage message) {
        log.info("Received refund processing job for return: {}, order: {}",
                message.getReturnRequestId(), message.getOrderNumber());

        try {
            // Call mock payment gateway
            String transactionId = paymentGatewayService.processRefund(
                    message.getOrderId(),
                    message.getRefundAmount(),
                    message.getCustomerEmail()
            );

            // Update return request with transaction ID
            returnRequestRepository.findById(message.getReturnRequestId()).ifPresent(returnRequest -> {
                returnRequest.setRefundTransactionId(transactionId);
                returnRequestRepository.save(returnRequest);
            });

            log.info("Refund processed successfully. Transaction ID: {} for order: {}",
                    transactionId, message.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to process refund for return: {}", message.getReturnRequestId(), e);
            throw e; // Re-throw to trigger DLQ
        }
    }
}
