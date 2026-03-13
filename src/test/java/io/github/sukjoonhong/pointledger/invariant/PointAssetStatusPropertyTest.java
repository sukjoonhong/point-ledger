package io.github.sukjoonhong.pointledger.invariant;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointAssetStatusPropertyTest {

    /**
     * 명제 1: CANCELLED 또는 EXPIRED 상태인 자산은 절대로 다시 ACTIVE가 될 수 없다.
     * (무작위 상태와 무작위 시도를 때려넣어 증명)
     */
    @Property
    void terminalStatesMustStayTerminal(
            @ForAll("terminalStatuses") PointAssetStatus currentStatus,
            @ForAll("transitionActions") String action
    ) {
        PointAsset asset = PointAsset.builder().status(currentStatus).remainingAmount(0L).build();

        switch (action) {
            case "cancel" -> assertThatThrownBy(asset::cancel)
                    .isInstanceOf(PointLedgerException.class)
                    .extracting("errorCode").isEqualTo(PointErrorCode.ASSET_NOT_ACTIVE);
            case "expire" -> assertThatThrownBy(asset::expire)
                    .isInstanceOf(PointLedgerException.class)
                    .extracting("errorCode").isEqualTo(PointErrorCode.ASSET_NOT_ACTIVE);
            case "deduct" -> assertThatThrownBy(() -> asset.deduct(1L))
                    .isInstanceOf(PointLedgerException.class)
                    .extracting("errorCode").isEqualTo(PointErrorCode.ASSET_NOT_ACTIVE);
        }

        assertThat(asset.getStatus()).isEqualTo(currentStatus);
    }

    /**
     * 명제 2: ACTIVE 상태에서만 상태 변화가 가능하다.
     */
    @Property
    void onlyActiveAssetCanTransition(
            @ForAll("transitionActions") String action
    ) {
        // given: 활성 상태의 자산
        PointAsset asset = PointAsset.builder()
                .status(PointAssetStatus.ACTIVE)
                .amount(2000L)
                .remainingAmount(2000L)
                .build();

        // when
        if (action.equals("cancel")) {
            asset.cancel();
            assertThat(asset.getStatus()).isEqualTo(PointAssetStatus.CANCELLED);
        } else if (action.equals("expire")) {
            asset.expire();
            assertThat(asset.getStatus()).isEqualTo(PointAssetStatus.EXPIRED);
        }
    }


    @Provide
    Arbitrary<PointAssetStatus> terminalStatuses() {
        return Arbitraries.of(PointAssetStatus.CANCELLED, PointAssetStatus.EXPIRED);
    }

    @Provide
    Arbitrary<String> transitionActions() {
        return Arbitraries.of("cancel", "expire", "deduct");
    }
}
