package com.articurated.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Queues
    public static final String INVOICE_GENERATION_QUEUE = "invoice.generation.queue";
    public static final String REFUND_PROCESSING_QUEUE = "refund.processing.queue";

    // Exchanges
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String RETURN_EXCHANGE = "return.exchange";

    // Routing keys
    public static final String INVOICE_ROUTING_KEY = "order.invoice.generate";
    public static final String REFUND_ROUTING_KEY = "return.refund.process";

    // Dead Letter
    public static final String DLQ_EXCHANGE = "dlq.exchange";
    public static final String INVOICE_DLQ = "invoice.generation.dlq";
    public static final String REFUND_DLQ = "refund.processing.dlq";

    @Bean
    public Queue invoiceGenerationQueue() {
        return QueueBuilder.durable(INVOICE_GENERATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", INVOICE_DLQ)
                .build();
    }

    @Bean
    public Queue refundProcessingQueue() {
        return QueueBuilder.durable(REFUND_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", REFUND_DLQ)
                .build();
    }

    @Bean
    public Queue invoiceDlq() {
        return QueueBuilder.durable(INVOICE_DLQ).build();
    }

    @Bean
    public Queue refundDlq() {
        return QueueBuilder.durable(REFUND_DLQ).build();
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange returnExchange() {
        return new TopicExchange(RETURN_EXCHANGE);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    public Binding invoiceBinding() {
        return BindingBuilder.bind(invoiceGenerationQueue())
                .to(orderExchange())
                .with(INVOICE_ROUTING_KEY);
    }

    @Bean
    public Binding refundBinding() {
        return BindingBuilder.bind(refundProcessingQueue())
                .to(returnExchange())
                .with(REFUND_ROUTING_KEY);
    }

    @Bean
    public Binding invoiceDlqBinding() {
        return BindingBuilder.bind(invoiceDlq()).to(dlqExchange()).with(INVOICE_DLQ);
    }

    @Bean
    public Binding refundDlqBinding() {
        return BindingBuilder.bind(refundDlq()).to(dlqExchange()).with(REFUND_DLQ);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        return factory;
    }
}
