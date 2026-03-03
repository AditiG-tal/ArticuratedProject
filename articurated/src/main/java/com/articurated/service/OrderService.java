package com.articurated.service;

import com.articurated.config.RabbitMQConfig;
import com.articurated.dto.OrderDto;
import com.articurated.entity.Order;
import com.articurated.entity.OrderItem;
import com.articurated.entity.OrderStatusHistory;
import com.articurated.enums.OrderStatus;
import com.articurated.exception.ResourceNotFoundException;
import com.articurated.jobs.Messages;
import com.articurated.repository.OrderRepository;
import com.articurated.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderStateMachine stateMachine;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public OrderDto.OrderResponse createOrder(OrderDto.CreateOrderRequest request) {
        // Build order items
        List<OrderItem> items = request.getItems().stream()
                .map(itemReq -> OrderItem.builder()
                        .productName(itemReq.getProductName())
                        .productSku(itemReq.getProductSku())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        // Calculate total
        BigDecimal total = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .totalAmount(total)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        // Set bidirectional relationship
        items.forEach(item -> item.setOrder(order));
        order.setItems(items);

        // Log initial status
        OrderStatusHistory initialHistory = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(OrderStatus.PENDING_PAYMENT)
                .toStatus(OrderStatus.PENDING_PAYMENT)
                .notes("Order created")
                .changedBy("SYSTEM")
                .build();
        order.getStatusHistory().add(initialHistory);

        Order saved = orderRepository.save(order);
        log.info("Created order: {}", saved.getOrderNumber());
        return mapToResponse(saved);
    }

    @Transactional
    public OrderDto.OrderResponse transitionStatus(UUID orderId, OrderDto.TransitionRequest request) {
        Order order = findOrderById(orderId);
        OrderStatus currentStatus = order.getStatus();
        OrderStatus targetStatus = request.getTargetStatus();

        // Validate transition via state machine
        stateMachine.validateTransition(currentStatus, targetStatus);

        // Record history before updating
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(currentStatus)
                .toStatus(targetStatus)
                .notes(request.getNotes())
                .changedBy(request.getChangedBy() != null ? request.getChangedBy() : "SYSTEM")
                .build();

        order.setStatus(targetStatus);
        order.getStatusHistory().add(history);
        Order saved = orderRepository.save(order);

        log.info("Order {} transitioned from {} to {}", order.getOrderNumber(), currentStatus, targetStatus);

        // Trigger background jobs based on transition
        if (targetStatus == OrderStatus.SHIPPED) {
            queueInvoiceGeneration(saved);
        }

        return mapToResponse(saved);
    }

    private void queueInvoiceGeneration(Order order) {
        List<Messages.OrderItemDetail> itemDetails = order.getItems().stream()
                .map(item -> new Messages.OrderItemDetail(
                        item.getProductName(),
                        item.getProductSku(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .collect(Collectors.toList());

        Messages.InvoiceGenerationMessage message = new Messages.InvoiceGenerationMessage(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                itemDetails
        );

        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.INVOICE_ROUTING_KEY, message);
        log.info("Queued invoice generation job for order: {}", order.getOrderNumber());
    }

    @Transactional(readOnly = true)
    public OrderDto.OrderResponse getOrder(UUID orderId) {
        return mapToResponse(findOrderById(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderDto.OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderDto.OrderStatusHistoryResponse> getOrderHistory(UUID orderId) {
        findOrderById(orderId); // verify exists
        return historyRepository.findByOrderIdOrderByChangedAtAsc(orderId).stream()
                .map(this::mapHistoryToResponse)
                .collect(Collectors.toList());
    }

    public Order findOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    public OrderDto.OrderResponse mapToResponse(Order order) {
        OrderDto.OrderResponse response = new OrderDto.OrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setCustomerName(order.getCustomerName());
        response.setCustomerEmail(order.getCustomerEmail());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setInvoicePath(order.getInvoicePath());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());

        if (order.getItems() != null) {
            response.setItems(order.getItems().stream()
                    .map(item -> {
                        OrderDto.OrderItemResponse itemResponse = new OrderDto.OrderItemResponse();
                        itemResponse.setId(item.getId());
                        itemResponse.setProductName(item.getProductName());
                        itemResponse.setProductSku(item.getProductSku());
                        itemResponse.setQuantity(item.getQuantity());
                        itemResponse.setUnitPrice(item.getUnitPrice());
                        return itemResponse;
                    })
                    .collect(Collectors.toList()));
        }
        return response;
    }

    private OrderDto.OrderStatusHistoryResponse mapHistoryToResponse(OrderStatusHistory h) {
        OrderDto.OrderStatusHistoryResponse r = new OrderDto.OrderStatusHistoryResponse();
        r.setId(h.getId());
        r.setFromStatus(h.getFromStatus());
        r.setToStatus(h.getToStatus());
        r.setNotes(h.getNotes());
        r.setChangedBy(h.getChangedBy());
        r.setChangedAt(h.getChangedAt());
        return r;
    }
}
