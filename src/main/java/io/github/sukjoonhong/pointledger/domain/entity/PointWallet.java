package io.github.sukjoonhong.pointledger.domain.entity;

import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointWalletStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Table(
        name = "point_wallet",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk__point_wallet__member_id", columnNames = {"member_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointWallet extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false)
    private Long balance;

    @Column(nullable = false)
    private Long lastSequenceNum;

    @Enumerated(EnumType.STRING)
    private PointWalletStatus status = PointWalletStatus.ACTIVE;

    public void markAsRecovering() {
        this.status = PointWalletStatus.RECOVERING;
    }

    public void activate() {
        this.status = PointWalletStatus.ACTIVE;
    }

    public boolean isRecovering() {
        return this.status == PointWalletStatus.RECOVERING;
    }

    /**
     * Applies transaction to wallet balance and updates sequence
     */
    public void apply(PointTransaction transaction, Long maxHoldingLimit) {
        validateSequence(transaction.getSequenceNum());

        switch (transaction.getType()) {
            case EARN, CANCEL_USE -> {
                validateMaxHoldingLimit(transaction.getAmount(), maxHoldingLimit);
                this.balance += transaction.getAmount();
            }
            case USE, CANCEL_EARN -> {
                validateBalance(transaction.getAmount());
                this.balance -= transaction.getAmount();
            }

            // ==========================================================
            // [!] CRITICAL NOTICE: RE_EARN(재적립) 처리 로직
            // ----------------------------------------------------------
            // 1. 설계 의도: 사용 취소 시 이미 만료된 자산에 대한 보상 처리
            // 2. 일관성 모델: Eventually Consistent (최종 일관성)
            // 3. 비고: 잔액 증가는 CANCEL_USE에서 선행됨 (Balance-Neutral)
            // ==========================================================
            case RE_EARN -> {
                /*
                 * 이 단계에서는 지갑 잔액을 변경하지 않습니다. 이유는 CANCEL_USE 단계에서
                 * 이미 전체 사용 취소 금액이 지갑 잔액에 합산되었기 때문입니다.
                 * * RE_EARN은 '만료된 자산의 재생성'을 원장에 기록하여 전체 시퀀스의
                 * 선형성(Linearity)을 보장하는 역할을 수행합니다.
                 */
            }
        }

        this.lastSequenceNum = transaction.getSequenceNum();
    }

    private void validateSequence(long incomingSeq) {
        if (incomingSeq <= this.lastSequenceNum) {
            throw new PointLedgerException(PointErrorCode.INVALID_SEQUENCE,
                    "Current: " + this.lastSequenceNum + ", Incoming: " + incomingSeq);
        }
    }

    private void validateMaxHoldingLimit(long amountToAdd, long maxLimit) {
        if (this.balance + amountToAdd > maxLimit) {
            throw new PointLedgerException(PointErrorCode.EXCEED_MAX_HOLDING_LIMIT, "Limit: " + maxLimit);
        }
    }

    private void validateBalance(long amountToDeduct) {
        if (this.balance < amountToDeduct) {
            throw new PointLedgerException(PointErrorCode.INSUFFICIENT_BALANCE, "Member: " + this.memberId);
        }
    }
}