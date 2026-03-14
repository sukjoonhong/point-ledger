package io.github.sukjoonhong.pointledger.unit;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointEarnServiceUnitTest {

    @Test
    @DisplayName("엣지 케이스: 1회 최대 적립 제한(10만)을 초과하면 자산 생성 단계에서 터져야 한다")
    void earnAmountExceedsSingleLimit() {
        PointWallet wallet = PointWallet.builder().id(1L).build();
        PointTransaction tx = PointTransaction.builder()
                .amount(100001L)
                .type(PointTransactionType.EARN)
                .build();
        BusinessTimeProvider timeProvider = Mockito.mock(BusinessTimeProvider.class);
        OffsetDateTime fixedNow = OffsetDateTime.parse("2026-03-14T10:00:00+09:00");
        Mockito.when(timeProvider.nowOffset()).thenReturn(fixedNow);

        assertThatThrownBy(() -> PointAsset
                .createActiveAsset(wallet, tx, 1L, 100000L, 30, timeProvider))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.INVALID_EARN_AMOUNT);
    }

    // PointWalletUnitTest
    @Test
    @DisplayName("엣지 케이스: 적립 후 잔액이 보유 한도를 초과하면 예외가 발생해야 한다")
    void balanceExceedsMaxHoldingLimit() {
        PointWallet wallet = PointWallet.builder().balance(900000L).lastSequenceNum(10L).build();
        PointTransaction tx = PointTransaction.builder().amount(200000L).type(PointTransactionType.EARN).sequenceNum(11L).build();

        assertThatThrownBy(() -> wallet.apply(tx, 1000000L))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EXCEED_MAX_HOLDING_LIMIT);
    }
}