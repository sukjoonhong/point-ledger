package io.github.sukjoonhong.pointledger.invariant;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

class PointDomainInvariantTest {

    // [추가] 모든 속성 테스트에서 공통으로 사용할 고정 시간 프로바이더 생성
    private final BusinessTimeProvider timeProvider = createMockTimeProvider();

    private BusinessTimeProvider createMockTimeProvider() {
        BusinessTimeProvider mock = Mockito.mock(BusinessTimeProvider.class);
        given(mock.nowOffset()).willReturn(OffsetDateTime.parse("2026-03-14T10:00:00+09:00"));
        return mock;
    }

    /**
     * 명제 1 (경제적 보존 법칙):
     * 지갑의 잔액은 언제나 ACTIVE 상태인 모든 자산의 잔액 합계와 일치해야 한다.
     */
    @Property(tries = 1000)
    void walletBalanceMustEqualTotalActiveAssetRemainingAmount(
            @ForAll @Size(min = 1, max = 30) List<@LongRange(min = 1, max = 100_000) Long> amounts,
            @ForAll @Size(min = 1, max = 30) List<@DoubleRange(max = 1.0) Double> statusThresholds
    ) {
        long totalSum = amounts.stream().mapToLong(Long::longValue).sum();
        long maxHoldingLimit = totalSum + 100_000L;

        PointWallet wallet = PointWallet.builder()
                .memberId(1L)
                .balance(0L)
                .lastSequenceNum(0L)
                .build();

        long expectedBalance = 0L;
        for (int i = 0; i < amounts.size(); i++) {
            long amount = amounts.get(i);
            long seq = (long) i + 1;

            PointAsset asset = createActiveAsset(wallet, amount, seq);

            double dice = statusThresholds.get(i % statusThresholds.size());
            if (dice < 0.2) {
                asset.cancel();
            } else if (dice < 0.4) {
                asset.expire();
            } else {
                expectedBalance += amount;
                wallet.apply(createEarnTx(amount, seq), maxHoldingLimit);
            }
        }

        assertThat(wallet.getBalance()).isEqualTo(expectedBalance);
    }

    /**
     * 명제 2 (정책 범위 내 적립):
     * 적립 금액이 정책 범위를 벗어나면 자산 생성이 거부되어야 한다.
     */
    @Property
    void earnAmountMustBeWithinPolicyRange(
            @ForAll @LongRange(min = 1, max = 100_000) long minLimit,
            @ForAll @LongRange(min = 100_001, max = 1_000_000) long maxLimit,
            @ForAll @LongRange(min = 1) long earnAmount
    ) {
        PointWallet wallet = PointWallet.builder().id(1L).build();
        PointTransaction tx = PointTransaction.builder().amount(earnAmount).build();

        if (earnAmount < minLimit || earnAmount > maxLimit) {
            assertThatThrownBy(() -> PointAsset.createActiveAsset(wallet, tx, minLimit, maxLimit, 30, timeProvider))
                    .isInstanceOf(PointLedgerException.class)
                    .extracting("errorCode").isEqualTo(PointErrorCode.INVALID_EARN_AMOUNT);
        } else {
            // [수정] 팩토리 메서드에 timeProvider 전달
            PointAsset asset = PointAsset.createActiveAsset(wallet, tx, minLimit, maxLimit, 30, timeProvider);
            assertThat(asset.getAmount()).isEqualTo(earnAmount);
        }
    }

    /**
     * 명제 3 (적립 취소의 무결성):
     * 단 1원이라도 사용(deduct)된 자산은 취소가 불가능하다.
     */
    @Property
    void usedAssetCannotBeCancelled(
            @ForAll @LongRange(min = 100, max = 1_000_000) long originalAmount,
            @ForAll @LongRange(min = 1) long usedAmount
    ) {
        Assume.that(usedAmount <= originalAmount);
        PointAsset asset = PointAsset.builder().amount(originalAmount).remainingAmount(originalAmount).status(PointAssetStatus.ACTIVE).build();
        asset.deduct(usedAmount);

        assertThatThrownBy(asset::cancel)
                .isInstanceOf(PointLedgerException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.ASSET_ALREADY_USED);
    }

    /**
     * 명제 4 (적립 취소의 가능성):
     * 미사용 자산은 취소 시 잔액이 0이 되고 상태가 CANCELLED로 변한다.
     */
    @Property
    void onlyUnusedAssetCanBeCancelled(
            @ForAll @LongRange(min = 1, max = 1_000_000) long amount
    ) {
        PointAsset asset = PointAsset.builder()
                .amount(amount)
                .remainingAmount(amount)
                .status(PointAssetStatus.ACTIVE)
                .build();

        asset.cancel();

        assertThat(asset.getStatus()).isEqualTo(PointAssetStatus.CANCELLED);
        assertThat(asset.getRemainingAmount()).isZero();
    }

    private PointAsset createActiveAsset(PointWallet wallet, long amount, long seq) {
        return PointAsset.builder()
                .walletId(wallet.getId())
                .amount(amount)
                .remainingAmount(amount)
                .status(PointAssetStatus.ACTIVE)
                .seqNum(seq)
                .expirationDate(timeProvider.nowOffset().plusDays(30)) // Mock 사용
                .build();
    }

    private PointTransaction createEarnTx(long amount, long seq) {
        return PointTransaction.builder()
                .amount(amount)
                .sequenceNum(seq)
                .type(PointTransactionType.EARN)
                .build();
    }
}