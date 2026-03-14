package io.github.sukjoonhong.pointledger.domain.entity;

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
        @Index(name = "idx_tx__point_key", columnList = "pointKey", unique = true), // 글로벌 멱등성 보장
        @Index(name = "idx_tx__original_key", columnList = "originalPointKey")      // 원본 추적용 인덱스
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;
    private Long amount;

    @Column(nullable = false)
    private String pointKey; // 이번 요청의 고유 키 (멱등성 기준)

    private String originalPointKey; // 원본 적립/사용의 키 (추적용)

    private Long sequenceNum;

    @Enumerated(EnumType.STRING)
    private PointTransactionType type;

    @Enumerated(EnumType.STRING)
    private PointSource source;

    private String orderId;
    private String description;

    @Builder
    public PointTransaction(Long id,
                            Long memberId,
                            Long amount,
                            String pointKey,
                            String originalPointKey,
                            Long sequenceNum,
                            PointTransactionType type,
                            PointSource source,
                            String orderId,
                            String description) {
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
}