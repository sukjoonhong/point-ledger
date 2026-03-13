package io.github.sukjoonhong.pointledger.domain.entity;

import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointUsageStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_usage_detail", indexes = {
        @Index(name = "idx_usage__tx_id", columnList = "transactionId"),
        @Index(name = "idx_usage__asset_id", columnList = "pointAssetId"),
        @Index(name = "idx_usage__order_id", columnList = "orderId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointUsageDetail extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private Long pointAssetId;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private Long amountUsed;      // 최초 사용 금액 (불변)

    @Column(nullable = false)
    private Long amountRefunded;  // 누적 환불 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointUsageStatus status; // USED -> PARTIALLY_REFUNDED -> REFUNDED

    /**
     * 사용 취소(환불) 처리 - 오직 앞으로만 전진합니다.
     */
    public void refund(Long refundAmount) {
        if (this.status == PointUsageStatus.REFUNDED) {
            throw new PointLedgerException(PointErrorCode.ALREADY_FULLY_REFUNDED);
        }

        long totalRefundAfter = this.amountRefunded + refundAmount;
        if (totalRefundAfter > this.amountUsed) {
            throw new PointLedgerException(PointErrorCode.INVALID_REFUND_AMOUNT,
                    "Original: " + this.amountUsed + ", Requested Total: " + totalRefundAfter);
        }

        this.amountRefunded = totalRefundAfter;

        if (this.amountRefunded.equals(this.amountUsed)) {
            this.status = PointUsageStatus.REFUNDED;
        } else {
            this.status = PointUsageStatus.PARTIALLY_REFUNDED;
        }
    }
}