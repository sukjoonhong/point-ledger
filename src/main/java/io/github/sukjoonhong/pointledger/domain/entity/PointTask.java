package io.github.sukjoonhong.pointledger.domain.entity;

import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "point_task")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTask extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private PointTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(length = 1000)
    private String lastErrorMessage;

    @Builder
    public PointTask(Long id, PointTransaction transaction) {
        this.id = id;
        this.transaction = transaction;
        this.status = TaskStatus.READY;
        this.retryCount = 0;
    }

    public void complete() {
        this.status = TaskStatus.COMPLETED;
    }

    public void fail(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.retryCount++;
        this.lastErrorMessage = errorMessage;
    }

    /**
     * 재시도가 가능한 상태인지 확인합니다.
     */
    public boolean isRetryable(int maxLimit) {
        return this.retryCount < maxLimit;
    }
}