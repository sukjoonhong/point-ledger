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