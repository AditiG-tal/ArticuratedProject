package com.articurated.dto;

import com.articurated.enums.ReturnStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

public class ReturnDto {

    @Data
    public static class CreateReturnRequest {
        @NotBlank(message = "Return reason is required")
        private String reason;
    }

    @Data
    public static class TransitionRequest {
        @NotNull(message = "Target status is required")
        private ReturnStatus targetStatus;

        private String notes;
        private String changedBy = "SYSTEM";
        private String trackingNumber;  // For IN_TRANSIT transition
    }

    @Data
    public static class ReturnResponse {
        private UUID id;
        private UUID orderId;
        private String orderNumber;
        private ReturnStatus status;
        private String reason;
        private String reviewNotes;
        private String trackingNumber;
        private String refundTransactionId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class ReturnStatusHistoryResponse {
        private UUID id;
        private ReturnStatus fromStatus;
        private ReturnStatus toStatus;
        private String notes;
        private String changedBy;
        private LocalDateTime changedAt;
    }
}
