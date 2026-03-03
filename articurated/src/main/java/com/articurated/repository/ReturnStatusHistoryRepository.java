package com.articurated.repository;

import com.articurated.entity.ReturnStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReturnStatusHistoryRepository extends JpaRepository<ReturnStatusHistory, UUID> {
    List<ReturnStatusHistory> findByReturnRequestIdOrderByChangedAtAsc(UUID returnRequestId);
}
