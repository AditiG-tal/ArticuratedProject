package com.articurated.controller;

import com.articurated.dto.ReturnDto;
import com.articurated.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Returns", description = "Return request lifecycle management endpoints")
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping("/orders/{orderId}/returns")
    @Operation(summary = "Initiate a return request",
            description = "Creates a return in REQUESTED state. Order must be in DELIVERED state.")
    public ResponseEntity<ReturnDto.ReturnResponse> createReturn(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReturnDto.CreateReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(returnService.createReturnRequest(orderId, request));
    }

    @GetMapping("/orders/{orderId}/returns")
    @Operation(summary = "Get all returns for an order")
    public ResponseEntity<List<ReturnDto.ReturnResponse>> getReturnsByOrder(
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(returnService.getReturnsByOrder(orderId));
    }

    @GetMapping("/returns/{returnId}")
    @Operation(summary = "Get return request by ID")
    public ResponseEntity<ReturnDto.ReturnResponse> getReturn(@PathVariable UUID returnId) {
        return ResponseEntity.ok(returnService.getReturn(returnId));
    }

    @PatchMapping("/returns/{returnId}/status")
    @Operation(summary = "Transition return status",
            description = "Valid transitions: REQUESTED->APPROVED/REJECTED, APPROVED->IN_TRANSIT, IN_TRANSIT->RECEIVED, RECEIVED->COMPLETED")
    public ResponseEntity<ReturnDto.ReturnResponse> transitionStatus(
            @PathVariable UUID returnId,
            @Valid @RequestBody ReturnDto.TransitionRequest request) {
        return ResponseEntity.ok(returnService.transitionStatus(returnId, request));
    }

    @GetMapping("/returns/{returnId}/history")
    @Operation(summary = "Get return status history", description = "Returns the audit trail of all return status changes")
    public ResponseEntity<List<ReturnDto.ReturnStatusHistoryResponse>> getReturnHistory(
            @PathVariable UUID returnId) {
        return ResponseEntity.ok(returnService.getReturnHistory(returnId));
    }
}
