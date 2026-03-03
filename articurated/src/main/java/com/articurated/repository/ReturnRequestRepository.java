package com.articurated.repository;

import com.articurated.entity.ReturnRequest;
import com.articurated.enums.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {
    List<ReturnRequest> findByOrderId(UUID orderId);
    List<ReturnRequest> findByStatus(ReturnStatus status);
}
