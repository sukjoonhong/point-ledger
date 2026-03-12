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

    @OneToOne(fetch = FetchType.LAZY) // Reference to the ledger
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private PointTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Builder
    public PointTask(PointTransaction transaction) {
        this.transaction = transaction;
        this.status = TaskStatus.READY;
    }

    public void complete() { this.status = TaskStatus.COMPLETED; }
    public void fail() { this.status = TaskStatus.FAILED; }
}