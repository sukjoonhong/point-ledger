package io.github.sukjoonhong.pointledger.unit;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.service.PointEarnService;
import io.github.sukjoonhong.pointledger.service.PointUseService;
import io.github.sukjoonhong.pointledger.service.replay.SequentialReplayStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SequentialReplayStrategyTest {

    private final Logger logger = LoggerFactory.getLogger(SequentialReplayStrategyTest.class);

    @Mock
    private PointEarnService earnService;

    @Mock
    private PointUseService useService;

    @Mock
    private PointPolicyManager policyManager;

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

        // NullPointerException prevention
        lenient().when(policyManager.getMaxFreePointHoldingLimit()).thenReturn(5_000_000L);
    }

    @Test
    @DisplayName("정상 흐름: 시퀀스 번호가 연속된 트랜잭션들이 주어지면 순차적으로 적용되어야 한다")
    void replay_Success_WithSequentialTransactions() {
        // given
        PointTransaction tx1 = createTx(11L, PointTransactionType.EARN, 500L);
        PointTransaction tx2 = createTx(12L, PointTransactionType.USE, 300L);
        List<PointTransaction> transactions = new ArrayList<>(Arrays.asList(tx1, tx2));

        // when
        replayStrategy.replay(wallet, transactions);

        // then
        assertThat(wallet.getLastSequenceNum()).isEqualTo(12L);
        assertThat(wallet.getBalance()).isEqualTo(1200L);
        verify(earnService, times(1)).handleEarn(eq(wallet), eq(tx1));
        verify(useService, times(1)).handleUse(eq(tx2));
    }

    @Test
    @DisplayName("엣지 케이스: 입력된 트랜잭션 리스트의 순서가 뒤섞여 있어도 시퀀스 번호순으로 정렬 후 적용되어야 한다")
    void replay_Success_WithUnsortedTransactions() {
        // given
        PointTransaction tx1 = createTx(11L, PointTransactionType.EARN, 1000L);
        PointTransaction tx2 = createTx(12L, PointTransactionType.CANCEL_EARN, 1000L);
        List<PointTransaction> transactions = new ArrayList<>(Arrays.asList(tx2, tx1));

        // when
        replayStrategy.replay(wallet, transactions);

        // then
        assertThat(wallet.getLastSequenceNum()).isEqualTo(12L);
        assertThat(wallet.getBalance()).isEqualTo(1000L);
        verify(earnService, times(1)).handleEarn(eq(wallet), eq(tx1));
        verify(earnService, times(1)).handleCancel(eq(tx2));
    }

    @Test
    @DisplayName("엣지 케이스: 지갑의 마지막 시퀀스와 이어지지 않는 갭(Gap)이 발생하면 예외를 던져야 한다")
    void replay_Fail_WhenSequenceGapDetected() {
        PointTransaction tx = createTx(12L, PointTransactionType.EARN, 500L); // 11 누락
        List<PointTransaction> transactions = new ArrayList<>(List.of(tx));

        assertThatThrownBy(() -> replayStrategy.replay(wallet, transactions))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.SEQUENCE_GAP_DETECTED);
    }

    @Test
    @DisplayName("엣지 케이스: 이미 처리된 시퀀스 번호(과거 번호)가 들어오면 예외를 던져야 한다")
    void replay_Fail_WhenOldSequenceProvided() {
        PointTransaction tx = createTx(10L, PointTransactionType.EARN, 500L);
        List<PointTransaction> transactions = new ArrayList<>(List.of(tx));

        assertThatThrownBy(() -> replayStrategy.replay(wallet, transactions))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.INVALID_SEQUENCE);
    }

    private PointTransaction createTx(Long seqNum, PointTransactionType type, Long amount) {
        return PointTransaction.builder()
                .memberId(1L)
                .amount(amount)
                .sequenceNum(seqNum)
                .type(type)
                .build();
    }
}