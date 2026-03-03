package com.articurated;

import com.articurated.dto.OrderDto;
import com.articurated.dto.ReturnDto;
import com.articurated.enums.OrderStatus;
import com.articurated.enums.ReturnStatus;
import com.articurated.exception.BusinessRuleException;
import com.articurated.exception.InvalidStateTransitionException;
import com.articurated.service.OrderService;
import com.articurated.service.ReturnService;
import org.junit.jupiter.api.BeforeEach;
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
class ReturnServiceIntegrationTest {

    @Autowired
    private ReturnService returnService;

    @Autowired
    private OrderService orderService;

    private UUID deliveredOrderId;

    @BeforeEach
    void setUpDeliveredOrder() {
        // Create and advance order to DELIVERED
        OrderDto.CreateOrderRequest req = new OrderDto.CreateOrderRequest();
        req.setCustomerName("Bob Jones");
        req.setCustomerEmail("bob@example.com");
        OrderDto.OrderItemRequest item = new OrderDto.OrderItemRequest();
        item.setProductName("Artisan Lamp");
        item.setProductSku("LAMP-001");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("199.00"));
        req.setItems(List.of(item));

        OrderDto.OrderResponse order = orderService.createOrder(req);
        deliveredOrderId = order.getId();

        for (OrderStatus target : new OrderStatus[]{
                OrderStatus.PAID, OrderStatus.PROCESSING_IN_WAREHOUSE,
                OrderStatus.SHIPPED, OrderStatus.DELIVERED}) {
            OrderDto.TransitionRequest tr = new OrderDto.TransitionRequest();
            tr.setTargetStatus(target);
            orderService.transitionStatus(deliveredOrderId, tr);
        }
    }

    @Test
    @DisplayName("Can initiate return for DELIVERED order")
    void initiateReturnForDeliveredOrder() {
        ReturnDto.CreateReturnRequest req = new ReturnDto.CreateReturnRequest();
        req.setReason("Item arrived damaged");

        ReturnDto.ReturnResponse response = returnService.createReturnRequest(deliveredOrderId, req);
        assertThat(response.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(response.getReason()).isEqualTo("Item arrived damaged");
    }

    @Test
    @DisplayName("Cannot initiate return for non-DELIVERED order")
    void cannotReturnNonDeliveredOrder() {
        // Create a PAID order
        OrderDto.CreateOrderRequest orderReq = new OrderDto.CreateOrderRequest();
        orderReq.setCustomerName("Carol");
        orderReq.setCustomerEmail("carol@example.com");
        OrderDto.OrderItemRequest item = new OrderDto.OrderItemRequest();
        item.setProductName("Bowl");
        item.setProductSku("BOWL-001");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.TEN);
        orderReq.setItems(List.of(item));
        OrderDto.OrderResponse paidOrder = orderService.createOrder(orderReq);

        OrderDto.TransitionRequest tr = new OrderDto.TransitionRequest();
        tr.setTargetStatus(OrderStatus.PAID);
        orderService.transitionStatus(paidOrder.getId(), tr);

        ReturnDto.CreateReturnRequest returnReq = new ReturnDto.CreateReturnRequest();
        returnReq.setReason("Changed my mind");

        assertThatThrownBy(() -> returnService.createReturnRequest(paidOrder.getId(), returnReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    @DisplayName("Approve return transitions REQUESTED -> APPROVED")
    void approveReturn() {
        ReturnDto.CreateReturnRequest req = new ReturnDto.CreateReturnRequest();
        req.setReason("Wrong item");
        ReturnDto.ReturnResponse created = returnService.createReturnRequest(deliveredOrderId, req);

        ReturnDto.TransitionRequest approveReq = new ReturnDto.TransitionRequest();
        approveReq.setTargetStatus(ReturnStatus.APPROVED);
        approveReq.setNotes("Approved by manager");
        approveReq.setChangedBy("manager-1");

        ReturnDto.ReturnResponse approved = returnService.transitionStatus(created.getId(), approveReq);
        assertThat(approved.getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(approved.getReviewNotes()).isEqualTo("Approved by manager");
    }

    @Test
    @DisplayName("Reject return transitions REQUESTED -> REJECTED")
    void rejectReturn() {
        ReturnDto.CreateReturnRequest req = new ReturnDto.CreateReturnRequest();
        req.setReason("Don't like it");
        ReturnDto.ReturnResponse created = returnService.createReturnRequest(deliveredOrderId, req);

        ReturnDto.TransitionRequest rejectReq = new ReturnDto.TransitionRequest();
        rejectReq.setTargetStatus(ReturnStatus.REJECTED);
        rejectReq.setNotes("Outside return window");
        rejectReq.setChangedBy("manager-1");

        ReturnDto.ReturnResponse rejected = returnService.transitionStatus(created.getId(), rejectReq);
        assertThat(rejected.getStatus()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    @DisplayName("Full return workflow REQUESTED -> APPROVED -> IN_TRANSIT -> RECEIVED -> COMPLETED")
    void fullReturnWorkflow() {
        ReturnDto.CreateReturnRequest req = new ReturnDto.CreateReturnRequest();
        req.setReason("Defective product");
        ReturnDto.ReturnResponse returnResp = returnService.createReturnRequest(deliveredOrderId, req);
        UUID returnId = returnResp.getId();

        // Approve
        applyTransition(returnId, ReturnStatus.APPROVED, "Approved", "manager");
        // Customer ships
        ReturnDto.TransitionRequest inTransitReq = new ReturnDto.TransitionRequest();
        inTransitReq.setTargetStatus(ReturnStatus.IN_TRANSIT);
        inTransitReq.setTrackingNumber("TRK-12345");
        inTransitReq.setChangedBy("customer");
        returnService.transitionStatus(returnId, inTransitReq);
        // Warehouse receives
        applyTransition(returnId, ReturnStatus.RECEIVED, "Item received in warehouse", "warehouse");
        // Complete
        ReturnDto.ReturnResponse completed = applyTransition(returnId, ReturnStatus.COMPLETED, "Refund processed", "finance");

        assertThat(completed.getStatus()).isEqualTo(ReturnStatus.COMPLETED);
        assertThat(completed.getTrackingNumber()).isEqualTo("TRK-12345");
    }

    @Test
    @DisplayName("Cannot skip states in return workflow")
    void cannotSkipStates() {
        ReturnDto.CreateReturnRequest req = new ReturnDto.CreateReturnRequest();
        req.setReason("Defective");
        ReturnDto.ReturnResponse returnResp = returnService.createReturnRequest(deliveredOrderId, req);

        ReturnDto.TransitionRequest skipReq = new ReturnDto.TransitionRequest();
        skipReq.setTargetStatus(ReturnStatus.COMPLETED);

        assertThatThrownBy(() -> returnService.transitionStatus(returnResp.getId(), skipReq))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Return history is tracked")
    void returnHistoryIsTracked() {
        ReturnDto.CreateReturnRequest req = new ReturnDto.CreateReturnRequest();
        req.setReason("Broken");
        ReturnDto.ReturnResponse returnResp = returnService.createReturnRequest(deliveredOrderId, req);
        applyTransition(returnResp.getId(), ReturnStatus.APPROVED, "Approved", "manager");

        List<ReturnDto.ReturnStatusHistoryResponse> history =
                returnService.getReturnHistory(returnResp.getId());

        assertThat(history).hasSizeGreaterThanOrEqualTo(1);
    }

    private ReturnDto.ReturnResponse applyTransition(UUID returnId, ReturnStatus target,
                                                      String notes, String changedBy) {
        ReturnDto.TransitionRequest req = new ReturnDto.TransitionRequest();
        req.setTargetStatus(target);
        req.setNotes(notes);
        req.setChangedBy(changedBy);
        return returnService.transitionStatus(returnId, req);
    }
}
