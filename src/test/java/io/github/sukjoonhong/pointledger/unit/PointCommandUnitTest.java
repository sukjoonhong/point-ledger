package io.github.sukjoonhong.pointledger.unit;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointCommandUnitTest {

    @Test
    @DisplayName("엣지 케이스: 금액이 1 미만인 경우 생성 단계에서 예외가 발생해야 한다")
    void amountUnderMinimum() {
        assertThatThrownBy(() -> PointCommand.builder()
                .amount(0L)
                .type(PointTransactionType.EARN)
                .build()
        ).isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("엣지 케이스: USE 타입인데 orderId가 없으면 예외가 발생해야 한다")
    void useTypeRequiresOrderId() {
        assertThatThrownBy(() -> PointCommand.builder()
                .amount(100L)
                .type(PointTransactionType.USE)
                .orderId(null)
                .build()
        ).isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.ORDER_ID_REQUIRED);
    }
}