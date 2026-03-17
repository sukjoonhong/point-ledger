package io.github.sukjoonhong.pointledger.unit;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSequenceStatus;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.application.service.PointBusinessRouter;
import io.github.sukjoonhong.pointledger.application.service.PointSequenceValidator;
import io.github.sukjoonhong.pointledger.application.service.replay.SequentialReplayStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SequentialReplayStrategyTest {
    @Mock private PointBusinessRouter businessRouter;
    @Mock private PointSequenceValidator sequenceValidator;
    @Mock private PointPolicyManager policyManager;

    @InjectMocks
    private SequentialReplayStrategy replayStrategy;

    private PointWallet wallet;

    @BeforeEach
    void setUp() {
        wallet = PointWallet.builder()
                .memberId(1L)
                .balance(1000L)
                .lastSequenceNum(10L)
                .build();

        lenient().when(policyManager.getMaxFreePointHoldingLimit()).thenReturn(5_000_000L);
    }

    @Test
    @DisplayName("정상 흐름: 시퀀스 번호가 연속된 트랜잭션들이 주어지면 순차적으로 적용되어야 한다")
    void replay_Success_WithSequentialTransactions() {
        // given
        PointTransaction tx1 = createTx(11L, PointTransactionType.EARN, 500L);
        PointTransaction tx2 = createTx(12L, PointTransactionType.USE, 300L);
        List<PointTransaction> transactions = new ArrayList<>(Arrays.asList(tx1, tx2));

        given(sequenceValidator.validate(anyLong(), anyLong())).willReturn(PointSequenceStatus.EXPECTED);

        // when
        replayStrategy.replay(wallet, transactions);

        // then
        assertThat(wallet.getLastSequenceNum()).isEqualTo(12L);
        // [주의] wallet.apply() 로직은 Wallet 엔티티 내부에 있으므로 결과값 검증
        assertThat(wallet.getBalance()).isEqualTo(1200L);

        verify(businessRouter, times(1)).route(eq(wallet), eq(tx1));
        verify(businessRouter, times(1)).route(eq(wallet), eq(tx2));
    }

    @Test
    @DisplayName("엣지 케이스: 지갑의 마지막 시퀀스와 이어지지 않는 갭(Gap)이 발생하면 예외를 던져야 한다")
    void replay_Fail_WhenSequenceGapDetected() {
        // given
        PointTransaction tx = createTx(12L, PointTransactionType.EARN, 500L);
        List<PointTransaction> transactions = new ArrayList<>(List.of(tx));

        given(sequenceValidator.validate(eq(10L), eq(12L))).willReturn(PointSequenceStatus.GAP_DETECTED);

        // when & then
        assertThatThrownBy(() -> replayStrategy.replay(wallet, transactions))
                .isInstanceOf(PointLedgerException.class)
                .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.SEQUENCE_GAP_DETECTED);
    }

    @Test
    @DisplayName("엣지 케이스: 이미 처리된 시퀀스 번호(과거 번호)가 들어오면 예외를 던져야 한다")
    void replay_Fail_WhenOldSequenceProvided() {
        // given
        PointTransaction tx = createTx(10L, PointTransactionType.EARN, 500L);
        List<PointTransaction> transactions = new ArrayList<>(List.of(tx));

        given(sequenceValidator.validate(eq(10L), eq(10L))).willReturn(PointSequenceStatus.ALREADY_PROCESSED);

        // when & then
        assertThatThrownBy(() -> replayStrategy.replay(wallet, transactions))
                .isInstanceOf(PointLedgerException.class)
                .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INVALID_SEQUENCE);
    }

    private PointTransaction createTx(Long seqNum, PointTransactionType type, Long amount) {
        return PointTransaction.builder()
                .memberId(1L)
                .amount(amount)
                .sequenceNum(seqNum)
                .type(type)
                .pointKey("TX-" + seqNum) // pointKey 누락 방지
                .build();
    }
}