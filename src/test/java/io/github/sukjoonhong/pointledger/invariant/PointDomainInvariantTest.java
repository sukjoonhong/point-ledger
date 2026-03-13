package io.github.sukjoonhong.pointledger.invariant;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointDomainInvariantTest {

    /**
     * 명제 1 (경제적 보존 법칙):
     * 지갑의 잔액은 언제나 ACTIVE 상태인 모든 자산의 잔액 합계와 일치해야 한다.
     * 이제 apply 시 보유 한도(maxHoldingLimit)를 함께 검증합니다.
     */
    @Property(tries = 1000)
    void walletBalanceMustEqualTotalActiveAssetRemainingAmount(
            @ForAll @Size(min = 1, max = 30) List<@LongRange(min = 1, max = 100_000) Long> amounts,
            @ForAll @Size(min = 1, max = 30) List<@DoubleRange(max = 1.0) Double> statusThresholds
    ) {
        // given: 지갑 생성 및 모든 적립을 수용할 수 있는 무작위 한도 설정
        long totalSum = amounts.stream().mapToLong(Long::longValue).sum();
        long maxHoldingLimit = totalSum + 100_000L; // 적립 성공을 보장하기 위한 넉넉한 한도

        PointWallet wallet = PointWallet.builder()
                .memberId(1L)
                .balance(0L)
                .lastSequenceNum(0L)
                .build();

        // when: 무작위 자산 생성 및 상태 변화 시뮬레이션
        long expectedBalance = 0L;
        for (int i = 0; i < amounts.size(); i++) {
            long amount = amounts.get(i);
            long seq = (long) i + 1;

            // 자산 생성 (여기서는 직접 빌더 사용 - 상태 전이 테스트 목적)
            PointAsset asset = createActiveAsset(wallet, amount, seq);

            double dice = statusThresholds.get(i % statusThresholds.size());
            if (dice < 0.2) { // 20% 취소
                asset.cancel();
            } else if (dice < 0.4) { // 20% 만료
                asset.expire();
            } else { // 60% 유지 및 지갑 반영
                expectedBalance += amount;
                wallet.apply(createEarnTx(amount, seq), maxHoldingLimit);
            }
        }

        // then: [Invariant] 지갑 잔액 == sum(ACTIVE Assets)
        assertThat(wallet.getBalance()).isEqualTo(expectedBalance);
    }

    /**
     * 명제 2 (사용 상세 내역의 한계 법칙):
     * 누적 환불액은 최초 사용 금액을 초과할 수 없다.
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
            assertThatThrownBy(() -> PointAsset.createActiveAsset(wallet, tx, minLimit, maxLimit, 30))
                    .isInstanceOf(PointLedgerException.class)
                    .extracting("errorCode").isEqualTo(PointErrorCode.INVALID_EARN_AMOUNT);
        } else {
            PointAsset asset = PointAsset.createActiveAsset(wallet, tx, minLimit, maxLimit, 30);
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