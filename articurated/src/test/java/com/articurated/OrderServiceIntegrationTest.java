package com.articurated;

import com.articurated.dto.OrderDto;
import com.articurated.enums.OrderStatus;
import com.articurated.exception.InvalidStateTransitionException;
import com.articurated.exception.ResourceNotFoundException;
import com.articurated.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.context.annotation.Import(TestConfig.class)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    private OrderDto.CreateOrderRequest buildCreateRequest() {
        OrderDto.CreateOrderRequest req = new OrderDto.CreateOrderRequest();
        req.setCustomerName("Alice Smith");
        req.setCustomerEmail("alice@example.com");

        OrderDto.OrderItemRequest item = new OrderDto.OrderItemRequest();
        item.setProductName("Handcrafted Vase");
        item.setProductSku("VASE-001");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("49.99"));

        req.setItems(List.of(item));
        return req;
    }

    @Test
    @DisplayName("Create order sets PENDING_PAYMENT status and calculates total")
    void createOrder() {
        OrderDto.OrderResponse response = orderService.createOrder(buildCreateRequest());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("99.98");
        assertThat(response.getOrderNumber()).startsWith("ORD-");
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("Transition order PENDING_PAYMENT -> PAID succeeds")
    void transitionToPaid() {
        OrderDto.OrderResponse order = orderService.createOrder(buildCreateRequest());

        OrderDto.TransitionRequest req = new OrderDto.TransitionRequest();
        req.setTargetStatus(OrderStatus.PAID);
        req.setChangedBy("payment-service");

        OrderDto.OrderResponse updated = orderService.transitionStatus(order.getId(), req);
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Invalid state transition throws exception")
    void invalidTransitionThrows() {
        OrderDto.OrderResponse order = orderService.createOrder(buildCreateRequest());

        OrderDto.TransitionRequest req = new OrderDto.TransitionRequest();
        req.setTargetStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> orderService.transitionStatus(order.getId(), req))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Get order by unknown ID throws ResourceNotFoundException")
    void getUnknownOrderThrows() {
        assertThatThrownBy(() -> orderService.getOrder(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Order status history is recorded on each transition")
    void orderHistoryIsRecorded() {
        OrderDto.OrderResponse order = orderService.createOrder(buildCreateRequest());

        // Transition PENDING_PAYMENT -> PAID
        OrderDto.TransitionRequest req1 = new OrderDto.TransitionRequest();
        req1.setTargetStatus(OrderStatus.PAID);
        req1.setChangedBy("payment-service");
        orderService.transitionStatus(order.getId(), req1);

        // Transition PAID -> PROCESSING_IN_WAREHOUSE
        OrderDto.TransitionRequest req2 = new OrderDto.TransitionRequest();
        req2.setTargetStatus(OrderStatus.PROCESSING_IN_WAREHOUSE);
        req2.setChangedBy("warehouse");
        orderService.transitionStatus(order.getId(), req2);

        List<OrderDto.OrderStatusHistoryResponse> history = orderService.getOrderHistory(order.getId());
        // Initial + 2 transitions
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Cancel order from PENDING_PAYMENT state")
    void cancelOrderFromPendingPayment() {
        OrderDto.OrderResponse order = orderService.createOrder(buildCreateRequest());

        OrderDto.TransitionRequest req = new OrderDto.TransitionRequest();
        req.setTargetStatus(OrderStatus.CANCELLED);
        req.setNotes("Customer cancelled");

        OrderDto.OrderResponse cancelled = orderService.transitionStatus(order.getId(), req);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Cannot cancel order from PROCESSING_IN_WAREHOUSE")
    void cannotCancelFromProcessing() {
        OrderDto.OrderResponse order = orderService.createOrder(buildCreateRequest());

        // Advance to PROCESSING_IN_WAREHOUSE
        for (OrderStatus target : new OrderStatus[]{OrderStatus.PAID, OrderStatus.PROCESSING_IN_WAREHOUSE}) {
            OrderDto.TransitionRequest req = new OrderDto.TransitionRequest();
            req.setTargetStatus(target);
            orderService.transitionStatus(order.getId(), req);
        }

        OrderDto.TransitionRequest cancelReq = new OrderDto.TransitionRequest();
        cancelReq.setTargetStatus(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderService.transitionStatus(order.getId(), cancelReq))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
