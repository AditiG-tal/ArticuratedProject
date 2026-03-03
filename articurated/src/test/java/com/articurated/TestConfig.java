package com.articurated;

import com.articurated.jobs.Messages;
import com.articurated.repository.OrderRepository;
import com.articurated.repository.ReturnRequestRepository;
import com.articurated.service.MockPaymentGatewayService;
import com.articurated.service.PdfInvoiceService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test configuration that mocks external dependencies (RabbitMQ, PDF, Payment Gateway)
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public RabbitTemplate mockRabbitTemplate() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        doNothing().when(mock).convertAndSend(anyString(), anyString(), any(Object.class));
        return mock;
    }

    @Bean
    @Primary
    public PdfInvoiceService mockPdfInvoiceService() {
        PdfInvoiceService mock = mock(PdfInvoiceService.class);
        when(mock.generateInvoice(any(Messages.InvoiceGenerationMessage.class)))
                .thenReturn("/tmp/test-invoice.pdf");
        doNothing().when(mock).simulateSendEmail(anyString(), anyString(), anyString());
        return mock;
    }

    @Bean
    @Primary
    public MockPaymentGatewayService mockPaymentGatewayService() {
        MockPaymentGatewayService mock = mock(MockPaymentGatewayService.class);
        when(mock.processRefund(any(), any(), anyString())).thenReturn("TXN-TEST-123");
        return mock;
    }
}
