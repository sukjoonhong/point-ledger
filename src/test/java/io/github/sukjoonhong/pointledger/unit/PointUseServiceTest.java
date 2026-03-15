package io.github.sukjoonhong.pointledger.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.entity.*;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import io.github.sukjoonhong.pointledger.repository.PointUsageDetailRepository;
import io.github.sukjoonhong.pointledger.service.PointUseService;
import io.github.sukjoonhong.pointledger.service.event.PointOutboxCapturedEvent;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointUseServiceTest {

    @Mock private PointAssetRepository assetRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private PointUsageDetailRepository usageDetailRepository;
    @Mock private PointOutboxRepository outboxRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BusinessTimeProvider timeProvider;

    @InjectMocks
    private PointUseService pointUseService;

    private final Long memberId = 1L;
    private final OffsetDateTime now = OffsetDateTime.parse("2026-03-14T12:00:00+09:00");

    @BeforeEach
    void setUp() {
        // [중요] 모든 시간 기반 로직은 이 시간을 기준으로 동작하게 고정
        lenient().when(timeProvider.nowOffset()).thenReturn(now);
    }

    @Test
    @DisplayName("성공: 여러 자산에서 포인트를 순차적으로 차감한다")
    void handleUse_Success() {
        // given
        PointTransaction tx = createUseTx(1500L);
        PointAsset asset1 = createAsset(1L, 1000L);
        PointAsset asset2 = createAsset(2L, 1000L);

        given(assetRepository.findAllForDeduction(eq(memberId), any(Sort.class))).willReturn(List.of(asset1, asset2));

        // when
        pointUseService.handleUse(tx);

        // then
        assertThat(asset1.getRemainingAmount()).isZero();     // 1000원 전액 차감
        assertThat(asset2.getRemainingAmount()).isEqualTo(500L); // 500원 남음
        verify(usageDetailRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("실패: 전체 자산 잔액이 부족하면 예외를 던진다")
    void handleUse_InsufficientBalance() {
        // given
        PointTransaction tx = createUseTx(3000L);
        PointAsset asset = createAsset(1L, 1000L);
        given(assetRepository.findAllForDeduction(eq(memberId), any(Sort.class))).willReturn(List.of(asset));

        // when & then
        assertThatThrownBy(() -> pointUseService.handleUse(tx))
                .isInstanceOf(PointLedgerException.class)
                .hasMessageContaining("Insufficient total point balance");
    }

    @Test
    @DisplayName("사용 취소: 자산이 만료되지 않았으면 원본 자산으로 복구한다")
    void handleCancel_RestoreToAsset() {
        // given
        PointTransaction tx = createCancelTx(500L);
        PointUsageDetail detail = createDetail(1L, 500L);
        PointAsset asset = createAsset(1L, 1000L); // 만료 전 자산
        asset.deduct(500L); // 500원 사용된 상태

        given(usageDetailRepository.findAllForRefund(eq(tx.getOrderId()), any(Sort.class))).willReturn(List.of(detail));
        given(assetRepository.findById(1L)).willReturn(Optional.of(asset));

        // when
        pointUseService.handleCancel(null, tx);

        // then
        assertThat(asset.getRemainingAmount()).isEqualTo(1000L); // 복구 완료
        verify(outboxRepository, never()).save(any()); // 보상 적립 미발생
    }

    @Test
    @DisplayName("사용 취소: 자산이 이미 만료되었다면 보상 적립(Outbox)을 발행한다")
    void handleCancel_Compensation() throws Exception {
        // given
        PointTransaction tx = createCancelTx(500L);
        PointUsageDetail detail = createDetail(1L, 500L);
        OffsetDateTime now = OffsetDateTime.now();

        // 1. 만료된 자산 생성
        PointAsset expiredAsset = PointAsset.builder()
                .id(1L)
                .amount(1000L)
                .remainingAmount(500L)
                .status(PointAssetStatus.ACTIVE)
                .expirationDate(now.minusDays(1))
                .build();

        // 2. Outbox Mocking (NPE 방지 및 ID 주입)
        PointOutbox mockOutbox = PointOutbox.builder()
                .id(777L)
                .status(PointOutbox.OutboxStatus.PENDING)
                .build();

        given(usageDetailRepository.findAllForRefund(eq(tx.getOrderId()), any(Sort.class)))
                .willReturn(List.of(detail));
        given(assetRepository.findById(1L)).willReturn(Optional.of(expiredAsset));
        given(timeProvider.nowOffset()).willReturn(now);

        // [핵심] Outbox 저장 시 객체를 반환해줘야 getId()가 작동함
        given(outboxRepository.save(any(PointOutbox.class))).willReturn(mockOutbox);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        pointUseService.handleCancel(PointWallet.builder().memberId(1L).build(), tx);

        // then
        // 1. DB 저장 확인
        verify(outboxRepository, times(1)).save(any());

        // 2. 이벤트 전파 확인
        verify(eventPublisher, times(1)).publishEvent(any(PointOutboxCapturedEvent.class));

        // 3. 만료 자산은 건드리지 않았는지 확인
        verify(assetRepository, never()).save(expiredAsset);
    }

    // Helper Methods
    private PointTransaction createUseTx(Long amount) {
        return PointTransaction.builder().id(100L).memberId(memberId).amount(amount).orderId("ORDER-1").build();
    }

    private PointTransaction createCancelTx(Long amount) {
        return PointTransaction.builder().id(101L).memberId(memberId).amount(amount).orderId("ORDER-1").build();
    }

    private PointAsset createAsset(Long id, Long amount) {
        return PointAsset.builder()
                .id(id)
                .amount(amount)
                .remainingAmount(amount)
                .status(PointAssetStatus.ACTIVE)
                .expirationDate(now.plusDays(30)) // 넉넉한 만료일
                .build();
    }

    private PointUsageDetail createDetail(Long assetId, Long amount) {
        return PointUsageDetail.builder().pointAssetId(assetId).amountUsed(amount).amountRefunded(0L).build();
    }
}