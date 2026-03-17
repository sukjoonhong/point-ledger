package io.github.sukjoonhong.pointledger.integration;

import io.github.sukjoonhong.pointledger.application.api.v1.PointAdminController;
import io.github.sukjoonhong.pointledger.application.service.ingress.PointEventIngestor;
import io.github.sukjoonhong.pointledger.domain.dto.PointCancelUseRequest;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.dto.PointEarnRequest;
import io.github.sukjoonhong.pointledger.domain.dto.PointUseRequest;
import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.repository.*;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * 전사적 포인트 라이프사이클 통합 테스트.
 * 동기 Admin API와 비동기 Ingestor 파이프라인을 모두 검증합니다.
 */
@ActiveProfiles({"api", "worker", "local", "test"})
@SpringBootTest
class PointBusinessScenarioTest {

    @Autowired private PointAdminController adminController;
    @Autowired private PointEventIngestor eventIngestor;

    @Autowired private PointWalletRepository walletRepository;
    @Autowired private PointAssetRepository assetRepository;
    @Autowired private PointTransactionRepository transactionRepository;
    @Autowired private PointTaskRepository taskRepository;

    @MockitoSpyBean
    private BusinessTimeProvider timeProvider;

    private final Long memberId = 1000000000L;
    private final OffsetDateTime baseTime = OffsetDateTime.parse("2026-03-14T10:00:00+09:00");

    @BeforeEach
    @Transactional
    void cleanUp() {
        taskRepository.deleteAll();
        transactionRepository.deleteAllByMemberId(memberId);
        assetRepository.deleteAllByMemberId(memberId);
        walletRepository.deleteByMemberId(memberId);

        when(timeProvider.nowOffset()).thenReturn(baseTime);
    }

    @Test
    @DisplayName("골든 시나리오 (동기): A는 만료, B는 활성 상태인 정교한 타임라인 검증")
    void scenario_Synchronous_Full_Verification() {
        // 1. A 적립 (1000원) - 기준 시간(baseTime)
        adminController.issuePoints(new PointEarnRequest(memberId, 1000L, "A", null, "Earn A"));

        // ★ [핵심 1] 6개월 시간 점프!
        when(timeProvider.nowOffset()).thenReturn(baseTime.plusMonths(6));

        // 2. B 적립 (500원) - A보다 6개월 늦게 적립됨
        adminController.issuePoints(new PointEarnRequest(memberId, 500L, "B", null, "Earn B"));
        assertThat(getWalletBalance()).isEqualTo(1500L);

        // 3. C 사용 (1200원) -> A(1000) + B(200)
        adminController.deductPoints(new PointUseRequest(memberId, 1200L, "C", "A1234"));
        assertThat(getWalletBalance()).isEqualTo(300L);

        // ★ [핵심 2] 다시 7개월 점프! (기준 시간으로부터 총 13개월 경과)
        when(timeProvider.nowOffset()).thenReturn(baseTime.plusMonths(13));

        // [상황 판단]
        // A는 13개월 지났으므로 -> 만료됨 (RE_EARN 대상)
        // B는 7개월 지났으므로 -> 아직 활성 상태 (원복 대상)

        // 4. D 부분 취소 (1100원)
        adminController.revertDeduction(new PointCancelUseRequest(memberId, 1100L, "D", "A1234", "C"));

        // 5. 검증
        assertThat(getWalletBalance()).isEqualTo(1400L);

        // B는 200원 전액 원복되어 500원이 되어야 정상입니다
        PointAsset assetB = assetRepository.findByPointKey("B").orElseThrow();
        assertThat(assetB.getRemainingAmount()).as("B 자산은 200원이 원복되어 500원이어야 함").isEqualTo(500L);

        // A의 보상 적립(RE_EARN)은 1000원이 아니라 900원이 되어야 정상입니다
        boolean hasCompensation = transactionRepository.findAllByMemberId(memberId).stream()
                .anyMatch(tx -> tx.getType() == PointTransactionType.RE_EARN && tx.getAmount() == 900L);
        assertThat(hasCompensation).as("만료된 A를 대신해 900원 신규 적립(RE_EARN)이 발생해야 함").isTrue();
    }

    @Test
    @DisplayName("골든 시나리오 (비동기): Ingestor를 통한 동일 시나리오 검증")
    void scenario_Asynchronous_Full_Verification() {
        // 1. 적립 A (기준 시간)
        eventIngestor.enqueueEvent(createCommand(1000L, "A_ASYNC", 1L, PointTransactionType.EARN, null, null));
        await().atMost(5, TimeUnit.SECONDS).until(() -> getWalletBalance() == 1000L);

        // ★ 6개월 시간 점프 (A와 B의 만료 시점을 다르게 만듭니다)
        when(timeProvider.nowOffset()).thenReturn(baseTime.plusMonths(6));

        // 2. 적립 B (A보다 6개월 늦음)
        eventIngestor.enqueueEvent(createCommand(500L, "B_ASYNC", 2L, PointTransactionType.EARN, null, null));
        await().atMost(5, TimeUnit.SECONDS).until(() -> getWalletBalance() == 1500L);

        // 3. 사용 C (1200원 사용 -> A 1000원, B 200원 차감됨)
        eventIngestor.enqueueEvent(createCommand(1200L, "C_ASYNC", 3L, PointTransactionType.USE, "ORDER_ASYNC", null));
        await().atMost(5, TimeUnit.SECONDS).until(() -> getWalletBalance() == 300L);

        // ★ 다시 7개월 점프 (기준 시간으로부터 총 13개월 경과)
        // -> A는 13개월 지나서 '만료됨'
        // -> B는 7개월 지나서 '활성 상태'
        when(timeProvider.nowOffset()).thenReturn(baseTime.plusMonths(13));

        // 4. 부분 취소 D (1100원 취소)
        // -> LIFO에 의해 최근 쓴 B 200원 먼저 원복, 남은 900원은 만료된 A 대신 RE_EARN
        eventIngestor.enqueueEvent(createCommand(1100L, "D_ASYNC", 4L, PointTransactionType.CANCEL_USE, "ORDER_ASYNC", "C_ASYNC"));

        // 5. 최종 검증
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // (1) 전체 지갑 잔액 확인 (기존 300 + 취소 1100 = 1400)
            assertThat(getWalletBalance()).isEqualTo(1400L);

            // (2) B_ASYNC 자산 원복 확인 (LIFO 정책: 200원이 전액 복구되어 300 -> 500원이 됨)
            PointAsset assetB = assetRepository.findByPointKey("B_ASYNC")
                    .orElseThrow(() -> new AssertionError("Asset B_ASYNC not found"));
            assertThat(assetB.getRemainingAmount()).as("B 자산은 200원 원복되어 500원이어야 함").isEqualTo(500L);

            // (3) RE_EARN 발생 확인 (A 자산 복구분 900원에 대해 신규 적립 발생)
            boolean hasReEarn = transactionRepository.findAllByMemberId(memberId).stream()
                    .anyMatch(tx -> tx.getType() == PointTransactionType.RE_EARN && tx.getAmount() == 900L);
            assertThat(hasReEarn).as("만료된 건에 대해 900원 보상 적립이 발생해야 함").isTrue();
        });
    }

    @Test
    @DisplayName("시나리오: 동일한 시퀀스의 이벤트가 중복 접수되어도 멱등성이 유지되어야 한다")
    void scenario_Idempotency_Verification() {
        // 지갑이 없으면 1번부터, 있으면 다음 시퀀스부터 시작하도록
        long nextSeq = walletRepository.findByMemberId(memberId)
                .map(w -> w.getLastSequenceNum() + 1)
                .orElse(1L);

        // 1. 적립 이벤트 던짐
        PointCommand cmd = createCommand(1000L, "DUP-KEY", nextSeq, PointTransactionType.EARN, null, null);
        eventIngestor.enqueueEvent(cmd);

        // 정상적으로 지갑이 생성되고 1000원이 반영될 때까지 대기
        await().atMost(5, TimeUnit.SECONDS).until(() -> walletRepository.findByMemberId(memberId)
                .map(w -> w.getBalance() == 1000L)
                .orElse(false));

        // 2. 동일한 이벤트(동일 Seq)를 한 번 더 던짐
        eventIngestor.enqueueEvent(cmd);

        // 3. 중복 처리 방지 확인 (잔액은 그대로 1000원이어야 함)
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        long finalBalance = walletRepository.findByMemberId(memberId)
                .map(PointWallet::getBalance)
                .orElse(0L);

        assertThat(finalBalance).as("중복 이벤트로 인해 잔액이 이중으로 늘어나면 안 됨").isEqualTo(1000L);

        // 4. 트랜잭션 기록도 1건만 있어야 함
        long txCount = transactionRepository.findAllByMemberId(memberId).stream()
                .filter(t -> t.getPointKey().equals("DUP-KEY"))
                .count();
        assertThat(txCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("시나리오: 최대 보유 한도를 초과하는 적립 요청은 거부되고 상태가 유지되어야 한다")
    void scenario_MaxLimit_Policy_Verification() {
        long singleEarnLimit = 100000L;

        // 1. 10만 원씩 10번 적립하여 지갑 한도(100만)를 꽉 채웁니다.
        for (int i = 1; i <= 10; i++) {
            adminController.issuePoints(new PointEarnRequest(
                    memberId,
                    singleEarnLimit,
                    "FILL-" + i,
                    null,
                    "Filling wallet to limit")
            );
        }
        assertThat(getWalletBalance()).isEqualTo(1000000L);

        // 2. 이제 지갑이 꽉 찬 상태에서 추가로 10,000원 적립 시도
        // (이때는 1회 한도 10만은 지켰지만, 지갑 총 한도 100만을 초과하게 됩니다)
        PointEarnRequest overLimitRequest = new PointEarnRequest(
                memberId, 10000L, "OVER", null, "Try to exceed total limit");

        // 3. 지갑 한도 초과(EXCEED_MAX_FREE_POINT_LIMIT) 예외 발생 확인
        assertThatThrownBy(() -> adminController.issuePoints(overLimitRequest))
                .isInstanceOf(PointLedgerException.class);

        // 4. 잔액은 유실이나 초과 없이 여전히 1,000,000원이어야 함
        assertThat(getWalletBalance()).isEqualTo(1000000L);
    }

    @Test
    @DisplayName("시나리오: 만료된 자산과 활성 자산이 섞인 취소 시, 각각 보상적립과 원복으로 정확히 분리되어야 한다")
    void scenario_Complex_Mixed_Refund_Verification() {
        // 1. 자산 A(500원), B(500원) 적립
        adminController.issuePoints(new PointEarnRequest(memberId, 500L, "MIX-A", null, "A"));
        when(timeProvider.nowOffset()).thenReturn(baseTime.plusMonths(6));
        adminController.issuePoints(new PointEarnRequest(memberId, 500L, "MIX-B", null, "B"));

        // 2. 800원 사용 (A: 500원 전액, B: 300원 사용) -> 잔액 200원
        adminController.deductPoints(new PointUseRequest(memberId, 800L, "USE-1", "ORDER-MIX"));

        // 3. 시간 점프! (A는 적립 후 13개월 지남 -> 만료 / B는 7개월 지남 -> 활성)
        when(timeProvider.nowOffset()).thenReturn(baseTime.plusMonths(13));

        // 4. 800원 전액 취소 (LIFO 정책에 따라 B부터 환불 시작)
        // - B 사용분(300원) 환불: B는 살아있으므로 '원복(Restore)'
        // - A 사용분(500원) 환불: A는 만료됐으므로 '보상적립(RE_EARN)'
        adminController.revertDeduction(new PointCancelUseRequest(memberId, 800L, "REV-1", "ORDER-MIX", "USE-1"));

        // 5. 검증
        // (1) 지갑 잔액: 기존 200 + 취소 800 = 1000L
        assertThat(getWalletBalance()).isEqualTo(1000L);

        // (2) B 자산: 기존 200 + 원복 300 = 500L
        PointAsset assetB = assetRepository.findByPointKey("MIX-B").orElseThrow();
        assertThat(assetB.getRemainingAmount()).isEqualTo(500L);

        // (3) RE_EARN: 만료된 A를 대신해 500원 신규 적립 발생 확인
        boolean hasReEarn = transactionRepository.findAllByMemberId(memberId).stream()
                .anyMatch(tx -> tx.getType() == PointTransactionType.RE_EARN && tx.getAmount() == 500L);
        assertThat(hasReEarn).as("만료된 A분에 대해 500원 보상 적립이 발생해야 함").isTrue();
    }

    private PointCommand createCommand(Long amount, String key, Long seq, PointTransactionType type, String orderId, String originalKey) {
        return PointCommand.builder()
                .memberId(memberId).amount(amount).pointKey(key).sequenceNum(seq)
                .type(type).source(PointSource.ORDER).orderId(orderId).originalPointKey(originalKey)
                .build();
    }

    private Long getWalletBalance() {
        return walletRepository.findByMemberId(memberId)
                .map(PointWallet::getBalance)
                .orElse(0L);
    }
}