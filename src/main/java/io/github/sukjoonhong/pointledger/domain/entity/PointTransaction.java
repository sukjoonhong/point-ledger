package io.github.sukjoonhong.pointledger.domain.entity;

import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 거래 이력 엔티티
 * 모든 포인트 변동의 최종 근거가 되며, pointKey를 통해 멱등성을 보장함
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;
    private Long amount;

    @Column(unique = true)
    private String pointKey;

    private Long sequenceNum;

    @Enumerated(EnumType.STRING)
    private PointTransactionType type;

    @Enumerated(EnumType.STRING)
    private PointSource source;

    private String description;

    @Builder
    public PointTransaction(Long memberId, Long amount, String pointKey, Long sequenceNum,
                            PointTransactionType type, PointSource source, String description) {
        this.memberId = memberId;
        this.amount = amount;
        this.pointKey = pointKey;
        this.sequenceNum = sequenceNum;
        this.type = type;
        this.source = source;
        this.description = description;
    }
}