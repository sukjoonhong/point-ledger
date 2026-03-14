package io.github.sukjoonhong.pointledger.domain.entity;

import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "point_transaction", indexes = {
        @Index(name = "idx_tx__member_id_seq", columnList = "memberId, sequenceNum"),
        @Index(name = "idx_tx__point_key", columnList = "pointKey", unique = true),
        @Index(name = "idx_tx__original_key", columnList = "originalPointKey")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Column(nullable = false)
    private Long amount; // 원본 요청 금액

    private Long appliedAmount;

    @Column(nullable = false)
    private String pointKey;

    private String originalPointKey;

    private Long sequenceNum;

    @Enumerated(EnumType.STRING)
    private PointTransactionType type;

    @Enumerated(EnumType.STRING)
    private PointSource source;

    private String orderId;
    private String description;

    @Builder
    public PointTransaction(Long id, Long memberId, Long amount, String pointKey,
                            String originalPointKey, Long sequenceNum,
                            PointTransactionType type, PointSource source,
                            String orderId, String description) {
        this.id = id;
        this.memberId = memberId;
        this.amount = amount;
        this.pointKey = pointKey;
        this.originalPointKey = originalPointKey;
        this.sequenceNum = sequenceNum;
        this.type = type;
        this.source = source;
        this.orderId = orderId;
        this.description = description;
    }

    public void recordAppliedAmount(Long actualAppliedAmount) {
        if (this.appliedAmount != null) {
            throw new PointLedgerException(
                    PointErrorCode.TRANSACTION_ALREADY_APPLIED,
                    "TransactionID: " + this.id + " already has appliedAmount: " + this.appliedAmount
            );
        }
        this.appliedAmount = actualAppliedAmount;
    }
}