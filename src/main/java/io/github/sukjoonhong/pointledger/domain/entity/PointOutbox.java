package io.github.sukjoonhong.pointledger.domain.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_outbox", indexes = {
        @Index(name = "idx_outbox__status_created", columnList = "status, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointOutbox extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status; // PENDING, PROCESSED, FAILED

    @Builder.Default
    private Integer retryCount = 0;

    @Getter
    public enum OutboxStatus {
        PENDING, PROCESSED, FAILED
    }

    public void complete() {
        this.status = OutboxStatus.PROCESSED;
    }

    public void fail() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
    }
}