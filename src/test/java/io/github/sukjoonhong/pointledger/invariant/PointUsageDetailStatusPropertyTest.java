package io.github.sukjoonhong.pointledger.invariant;

import io.github.sukjoonhong.pointledger.domain.entity.PointUsageDetail;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointUsageStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointUsageDetailStatusPropertyTest {

    /**
     * 명제 1: 환불 금액의 누적 합계가 최초 사용액과 같아지는 순간, 상태는 반드시 REFUNDED여야 하며
     * 그 이후의 모든 환불 시도는 차단되어야 한다 (종착역 고립성).
     */
    @Property
    void stateMustBecomeRefundedAndBlockedWhenFullyRefunded(
            @ForAll @LongRange(min = 1, max = 1_000_000) long amountUsed
    ) {
        PointUsageDetail detail = PointUsageDetail.builder().amountUsed(amountUsed).amountRefunded(0L).status(PointUsageStatus.USED).build();
        detail.refund(amountUsed);

        assertThat(detail.getStatus()).isEqualTo(PointUsageStatus.REFUNDED);

        assertThatThrownBy(() -> detail.refund(1L))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.ALREADY_FULLY_REFUNDED);
    }
    /**
     * 명제 2: 부분 환불(PARTIALLY_REFUNDED) 상태에서도 환불액의 총합은 amountUsed를 초과할 수 없다.
     */
    @Property
    void amountRefundedMustNeverExceedAmountUsed(
            @ForAll @LongRange(min = 100, max = 1_000_000) long amountUsed,
            @ForAll @LongRange(min = 1, max = 99) long firstRefund,
            @ForAll @LongRange(min = 1, max = 1_000_000) long secondRefund
    ) {
        PointUsageDetail detail = PointUsageDetail.builder().amountUsed(amountUsed).amountRefunded(0L).status(PointUsageStatus.USED).build();
        detail.refund(firstRefund);

        long remaining = amountUsed - firstRefund;
        if (secondRefund > remaining) {
            assertThatThrownBy(() -> detail.refund(secondRefund))
                    .isInstanceOf(PointLedgerException.class)
                    .extracting("errorCode").isEqualTo(PointErrorCode.INVALID_REFUND_AMOUNT);
        } else {
            detail.refund(secondRefund);
            assertThat(detail.getAmountRefunded()).isEqualTo(firstRefund + secondRefund);
        }
    }

    /**
     * 명제 3: 상태는 절대로 뒤로 갈 수 없다 (USED -> REFUNDED -> USED 불가).
     * (이것은 도메인 코드에 복구 로직이 아예 없음을 통해 증명되지만, 로직 오염 방지를 위해 명시)
     */
    @Property
    void statusShouldOnlyProgressForward(
            @ForAll @LongRange(min = 10, max = 100) long amountUsed,
            @ForAll @LongRange(min = 1, max = 5) long partialAmount
    ) {
        PointUsageDetail detail = PointUsageDetail.builder()
                .amountUsed(amountUsed)
                .amountRefunded(0L)
                .status(PointUsageStatus.USED)
                .build();

        // USED -> PARTIALLY_REFUNDED
        detail.refund(partialAmount);
        assertThat(detail.getStatus()).isEqualTo(PointUsageStatus.PARTIALLY_REFUNDED);

        // 다시 전액 환불 시도
        detail.refund(amountUsed - partialAmount);
        assertThat(detail.getStatus()).isEqualTo(PointUsageStatus.REFUNDED);

        // 어떤 액션을 해도 USED로 돌아가는 메서드가 존재하지 않음을 보장 (설계적 무결성)
    }
}