package io.github.sukjoonhong.pointledger.unit;

import io.github.sukjoonhong.pointledger.application.service.PointAssetManager;
import io.github.sukjoonhong.pointledger.application.service.PointOutboxService;
import io.github.sukjoonhong.pointledger.application.service.core.PointUseService;
import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointUsageDetail;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.repository.PointUsageDetailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointUseServiceTest {

    @Mock
    private PointAssetRepository assetRepository;
    @Mock
    private PointUsageDetailRepository usageDetailRepository;
    @Mock
    private PointAssetManager assetManager;
    @Mock
    private PointOutboxService outboxService;

    @InjectMocks
    private PointUseService pointUseService;

    private final Long memberId = 1L;

    @Test
    @DisplayName("Success: Deduct points sequentially using AssetManager")
    void handleUse_Success() {
        // given
        PointTransaction tx = createUseTx(1500L);
        List<PointAsset> assets = List.of(mock(PointAsset.class));

        PointAssetManager.DeductionResult result = new PointAssetManager.DeductionResult(
                List.of(mock(PointAsset.class)),
                List.of(mock(PointUsageDetail.class)),
                0L
        );

        given(assetRepository.findAllForDeduction(eq(memberId), any(Sort.class))).willReturn(assets);
        given(assetManager.deduct(tx, assets)).willReturn(result);

        // when
        pointUseService.handleUse(tx);

        // then
        verify(assetRepository, times(1)).saveAll(result.modifiedAssets());
        verify(usageDetailRepository, times(1)).saveAll(result.usageDetails());
    }

    @Test
    @DisplayName("Failure: Throw exception when balance is insufficient")
    void handleUse_InsufficientBalance() {
        // given
        PointTransaction tx = createUseTx(3000L);
        List<PointAsset> assets = List.of(mock(PointAsset.class));

        // 부족분이 500원 남은 상황 시뮬레이션
        PointAssetManager.DeductionResult result = new PointAssetManager.DeductionResult(
                List.of(), List.of(), 500L
        );

        given(assetRepository.findAllForDeduction(eq(memberId), any(Sort.class))).willReturn(assets);
        given(assetManager.deduct(tx, assets)).willReturn(result);

        // when & then
        assertThatThrownBy(() -> pointUseService.handleUse(tx))
                .isInstanceOf(PointLedgerException.class)
                .hasMessageContaining("Deficit: 500");
    }

    @Test
    @DisplayName("Cancel: Restore to original asset if not expired")
    void handleCancel_RestoreToAsset() {
        // given
        PointTransaction tx = createCancelTx(500L);
        PointAsset asset = mock(PointAsset.class);
        PointUsageDetail detail = mock(PointUsageDetail.class);

        PointAssetManager.RefundItem item = new PointAssetManager.RefundItem(asset, detail, 500L, false);

        given(usageDetailRepository.findAllForRefund(eq(tx.getOrderId()), any(Sort.class))).willReturn(List.of(detail));
        given(assetManager.calculateRefund(any(), any(), any(), any())).willReturn(List.of(item));

        // when
        pointUseService.handleCancel(null, tx);

        // then
        verify(asset, times(1)).restore(500L);
        verify(assetRepository, times(1)).save(asset);
    }

    @Test
    @DisplayName("Cancel: Create Re-Earn outbox if asset is expired")
    void handleCancel_Compensation() {
        // given
        PointTransaction tx = createCancelTx(500L);
        PointWallet wallet = PointWallet.builder().memberId(memberId).build();
        PointAsset asset = mock(PointAsset.class);

        // 만료된 아이템으로 설정
        PointAssetManager.RefundItem item = new PointAssetManager.RefundItem(asset, mock(PointUsageDetail.class), 500L, true);

        given(usageDetailRepository.findAllForRefund(any(), any())).willReturn(List.of());
        given(assetManager.calculateRefund(any(), any(), any(), any())).willReturn(List.of(item));

        // when
        pointUseService.handleCancel(wallet, tx);

        // then
        verify(outboxService, times(1)).createReEarnOutbox(eq(wallet), eq(tx), eq(500L), eq(tx.getSequenceNum() + 1));
        verify(asset, never()).restore(anyLong());
    }

    private PointTransaction createUseTx(Long amount) {
        return PointTransaction.builder().id(100L).memberId(memberId).amount(amount).orderId("ORDER-1").build();
    }

    private PointTransaction createCancelTx(Long amount) {
        return PointTransaction.builder().id(101L).memberId(memberId).amount(amount).orderId("ORDER-1").sequenceNum(1L).build();
    }
}