package io.github.sukjoonhong.pointledger.invariant;

import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointWalletPropertyTest {

    /**
     * Property 1: 적립(EARN) 시 한도 내라면 잔액이 정확히 증가해야 한다.
     */
    @Property
    void earnWithinLimitIncreasesBalance(
            @ForAll @LongRange(min = 0, max = 1_000_000L) long initialBalance,
            @ForAll @LongRange(min = 1, max = 500_000L) long earnAmount,
            @ForAll @LongRange(min = 1_500_000L, max = 2_000_000L) long maxLimit,
            @ForAll @LongRange(min = 1, max = 1000L) long seqNum
    ) {
        Assume.that(initialBalance + earnAmount <= maxLimit);
        PointWallet wallet = PointWallet.builder().balance(initialBalance).lastSequenceNum(0L).build();
        PointTransaction tx = PointTransaction.builder().amount(earnAmount).sequenceNum(seqNum).type(PointTransactionType.EARN).build();

        wallet.apply(tx, maxLimit);

        assertThat(wallet.getBalance()).isEqualTo(initialBalance + earnAmount);
    }

    /**
     * Property 2: 적립 시 보유 한도를 초과하면 반드시 예외가 발생해야 한다.
     */
    @Property
    void earnExceedingMaxLimitAlwaysThrows(
            @ForAll @LongRange(min = 500_000, max = 1_000_000L) long initialBalance,
            @ForAll @LongRange(min = 500_001, max = 1_000_000L) long earnAmount,
            @ForAll @LongRange(min = 1, max = 1_000_000L) long maxLimit,
            @ForAll @LongRange(min = 1) long seqNum
    ) {
        Assume.that(initialBalance + earnAmount > maxLimit);
        PointWallet wallet = PointWallet.builder().balance(initialBalance).lastSequenceNum(0L).build();
        PointTransaction tx = PointTransaction.builder().amount(earnAmount).sequenceNum(seqNum).type(PointTransactionType.EARN).build();

        assertThatThrownBy(() -> wallet.apply(tx, maxLimit))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EXCEED_MAX_HOLDING_LIMIT);
    }

    /**
     * Property 3: 잔액보다 큰 금액 사용(USE) 시 예외 발생 (한도 값은 의미 없음)
     */
    @Property
    void useExceedingBalanceAlwaysThrowsException(
            @ForAll @LongRange(min = 0, max = 10_000L) long initialBalance,
            @ForAll @LongRange(min = 10_001, max = 100_000L) long useAmount,
            @ForAll @LongRange(min = 1) long seqNum
    ) {
        PointWallet wallet = PointWallet.builder().balance(initialBalance).lastSequenceNum(0L).build();
        PointTransaction tx = PointTransaction.builder().amount(useAmount).sequenceNum(seqNum).type(PointTransactionType.USE).build();

        assertThatThrownBy(() -> wallet.apply(tx, 1_000_000L))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
    }

    /**
     * Property 4: 시퀀스 역전 또는 동일 시퀀스 인입 시 차단
     */
    @Property
    void invalidSequenceAlwaysBlocked(
            @ForAll @LongRange(min = 10, max = 1000L) long currentWalletSeq,
            @ForAll @LongRange(min = 1, max = 10L) long incomingTxSeq
    ) {
        PointWallet wallet = PointWallet.builder().balance(5000L).lastSequenceNum(currentWalletSeq).build();
        PointTransaction tx = PointTransaction.builder().amount(100L).sequenceNum(incomingTxSeq).type(PointTransactionType.EARN).build();

        assertThatThrownBy(() -> wallet.apply(tx, 5_000_000L))
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.INVALID_SEQUENCE);
    }
}