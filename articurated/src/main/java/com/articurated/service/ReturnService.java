package com.articurated.service;

import com.articurated.config.RabbitMQConfig;
import com.articurated.dto.ReturnDto;
import com.articurated.entity.Order;
import com.articurated.entity.ReturnRequest;
import com.articurated.entity.ReturnStatusHistory;
import com.articurated.enums.OrderStatus;
import com.articurated.enums.ReturnStatus;
import com.articurated.exception.BusinessRuleException;
import com.articurated.exception.ResourceNotFoundException;
import com.articurated.jobs.Messages;
import com.articurated.repository.ReturnRequestRepository;
import com.articurated.repository.ReturnStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnStatusHistoryRepository historyRepository;
    private final OrderService orderService;
    private final ReturnStateMachine stateMachine;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public ReturnDto.ReturnResponse createReturnRequest(UUID orderId, ReturnDto.CreateReturnRequest request) {
        Order order = orderService.findOrderById(orderId);

        // Business rule: returns only allowed for DELIVERED orders
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleException(
                    "Return can only be initiated for orders in DELIVERED state. Current state: " + order.getStatus());
        }

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .status(ReturnStatus.REQUESTED)
                .reason(request.getReason())
                .build();

        // Log initial history
        ReturnStatusHistory initialHistory = ReturnStatusHistory.builder()
                .returnRequest(returnRequest)
                .fromStatus(ReturnStatus.REQUESTED)
                .toStatus(ReturnStatus.REQUESTED)
                .notes("Return requested by customer")
                .changedBy("CUSTOMER")
                .build();
        returnRequest.getStatusHistory().add(initialHistory);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        log.info("Created return request {} for order {}", saved.getId(), order.getOrderNumber());
        return mapToResponse(saved);
    }

    @Transactional
    public ReturnDto.ReturnResponse transitionStatus(UUID returnId, ReturnDto.TransitionRequest request) {
        ReturnRequest returnRequest = findReturnById(returnId);
        ReturnStatus currentStatus = returnRequest.getStatus();
        ReturnStatus targetStatus = request.getTargetStatus();

        // Validate via state machine
        stateMachine.validateTransition(currentStatus, targetStatus);

        // Apply tracking number for IN_TRANSIT
        if (targetStatus == ReturnStatus.IN_TRANSIT && request.getTrackingNumber() != null) {
            returnRequest.setTrackingNumber(request.getTrackingNumber());
        }

        // Store review notes for APPROVED/REJECTED
        if (targetStatus == ReturnStatus.APPROVED || targetStatus == ReturnStatus.REJECTED) {
            returnRequest.setReviewNotes(request.getNotes());
        }

        // Record history
        ReturnStatusHistory history = ReturnStatusHistory.builder()
                .returnRequest(returnRequest)
                .fromStatus(currentStatus)
                .toStatus(targetStatus)
                .notes(request.getNotes())
                .changedBy(request.getChangedBy() != null ? request.getChangedBy() : "SYSTEM")
                .build();

        returnRequest.setStatus(targetStatus);
        returnRequest.getStatusHistory().add(history);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        log.info("Return {} transitioned from {} to {}", returnId, currentStatus, targetStatus);

        // Trigger refund job when COMPLETED
        if (targetStatus == ReturnStatus.COMPLETED) {
            queueRefundProcessing(saved);
        }

        return mapToResponse(saved);
    }

    private void queueRefundProcessing(ReturnRequest returnRequest) {
        Order order = returnRequest.getOrder();
        Messages.RefundProcessingMessage message = new Messages.RefundProcessingMessage(
                returnRequest.getId(),
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerEmail(),
                order.getTotalAmount()
        );

        rabbitTemplate.convertAndSend(RabbitMQConfig.RETURN_EXCHANGE, RabbitMQConfig.REFUND_ROUTING_KEY, message);
        log.info("Queued refund processing job for return: {}", returnRequest.getId());
    }

    @Transactional(readOnly = true)
    public ReturnDto.ReturnResponse getReturn(UUID returnId) {
        return mapToResponse(findReturnById(returnId));
    }

    @Transactional(readOnly = true)
    public List<ReturnDto.ReturnResponse> getReturnsByOrder(UUID orderId) {
        orderService.findOrderById(orderId); // verify order exists
        return returnRequestRepository.findByOrderId(orderId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReturnDto.ReturnStatusHistoryResponse> getReturnHistory(UUID returnId) {
        findReturnById(returnId);
        return historyRepository.findByReturnRequestIdOrderByChangedAtAsc(returnId).stream()
                .map(this::mapHistoryToResponse)
                .collect(Collectors.toList());
    }

    public ReturnRequest findReturnById(UUID returnId) {
        return returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found with id: " + returnId));
    }

    public ReturnDto.ReturnResponse mapToResponse(ReturnRequest r) {
        ReturnDto.ReturnResponse response = new ReturnDto.ReturnResponse();
        response.setId(r.getId());
        response.setOrderId(r.getOrder().getId());
        response.setOrderNumber(r.getOrder().getOrderNumber());
        response.setStatus(r.getStatus());
        response.setReason(r.getReason());
        response.setReviewNotes(r.getReviewNotes());
        response.setTrackingNumber(r.getTrackingNumber());
        response.setRefundTransactionId(r.getRefundTransactionId());
        response.setCreatedAt(r.getCreatedAt());
        response.setUpdatedAt(r.getUpdatedAt());
        return response;
    }

    private ReturnDto.ReturnStatusHistoryResponse mapHistoryToResponse(ReturnStatusHistory h) {
        ReturnDto.ReturnStatusHistoryResponse r = new ReturnDto.ReturnStatusHistoryResponse();
        r.setId(h.getId());
        r.setFromStatus(h.getFromStatus());
        r.setToStatus(h.getToStatus());
        r.setNotes(h.getNotes());
        r.setChangedBy(h.getChangedBy());
        r.setChangedAt(h.getChangedAt());
        return r;
    }
}
