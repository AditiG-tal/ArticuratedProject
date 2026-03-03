package com.articurated.entity;

import com.articurated.enums.ReturnStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "return_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus toStatus;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private String changedBy;

    @CreationTimestamp
    private LocalDateTime changedAt;
}
