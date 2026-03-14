package io.github.sukjoonhong.pointledger.domain.entity;

import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "point_asset", indexes = {
        @Index(name = "idx_asset__transaction_id", columnList = "transactionId"),
        @Index(name = "idx_asset__member_status", columnList = "memberId, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private String pointKey;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointSource source;

    @Column(nullable = false)
    private Integer sourcePriority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointAssetStatus status;

    @Column(nullable = false)
    private OffsetDateTime expirationDate;

    @Column(nullable = false)
    private Long seqNum;

    /**
     * 비즈니스 정책을 검증하며 활성 자산을 생성합니다.
     */
    public static PointAsset createActiveAsset(
            PointWallet wallet,
            PointTransaction transaction,
            Long minLimit,
            Long maxLimit,
            Integer expireDays,
            BusinessTimeProvider timeProvider
    ) {
        OffsetDateTime now = timeProvider.nowOffset();

        validateEarnAmount(transaction.getAmount(), minLimit, maxLimit);
        validateExpirationRange(expireDays, now);

        return PointAsset.builder()
                .walletId(wallet.getId())
                .memberId(transaction.getMemberId())
                .transactionId(transaction.getId())
                .pointKey(transaction.getPointKey())
                .amount(transaction.getAmount())
                .remainingAmount(transaction.getAmount())
                .status(PointAssetStatus.ACTIVE)
                .source(transaction.getSource())
                .expirationDate(now.plusDays(expireDays))
                .seqNum(transaction.getSequenceNum())
                .sourcePriority(transaction.getSource().getPriority())
                .build();
    }

    private static void validateExpirationRange(Integer expireDays, OffsetDateTime now) {
        OffsetDateTime targetDate = now.plusDays(expireDays);
        OffsetDateTime maxAllowedDate = now.plusYears(5);

        if (expireDays < 1) {
            throw new PointLedgerException(PointErrorCode.INVALID_EXPIRATION_RANGE, "Minimum 1 day required.");
        }
        if (!targetDate.isBefore(maxAllowedDate)) {
            throw new PointLedgerException(PointErrorCode.INVALID_EXPIRATION_RANGE, "Must be less than 5 years.");
        }
    }

    private static void validateEarnAmount(Long amount, Long min, Long max) {
        if (amount < min || amount > max) {
            throw new PointLedgerException(PointErrorCode.INVALID_EARN_AMOUNT,
                    String.format("Amount: %d, Allowed: [%d ~ %d]", amount, min, max));
        }
    }

    public boolean isExpired(BusinessTimeProvider timeProvider) {
        OffsetDateTime now = timeProvider.nowOffset();
        return this.status == PointAssetStatus.EXPIRED ||
                now.isAfter(this.expirationDate);
    }

    public void deduct(Long amountToDeduct) {
        ensureActive();
        if (this.remainingAmount < amountToDeduct) {
            throw new PointLedgerException(PointErrorCode.INSUFFICIENT_ASSET_BALANCE);
        }
        this.remainingAmount -= amountToDeduct;
    }

    public void cancel() {
        ensureActive();
        if (!this.amount.equals(this.remainingAmount)) {
            throw new PointLedgerException(PointErrorCode.ASSET_ALREADY_USED);
        }
        this.remainingAmount = 0L;
        this.status = PointAssetStatus.CANCELLED;
    }

    private void ensureActive() {
        if (this.status != PointAssetStatus.ACTIVE) {
            throw new PointLedgerException(PointErrorCode.ASSET_NOT_ACTIVE, "Current: " + this.status);
        }
    }

    public void expire() {
        ensureActive();

        this.status = PointAssetStatus.EXPIRED;
    }

    public void restore(Long amountToRestore) {
        ensureActive();

        this.remainingAmount += amountToRestore;
    }
}