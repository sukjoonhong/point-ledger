package io.github.sukjoonhong.pointledger.integration;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import io.github.sukjoonhong.pointledger.application.service.core.PointLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "point-ledger.policy.expire-days=365",
        "point-ledger.policy.max-free-point-holding-limit=1000000"
})
class PointLedgerServiceIntegrationTest {
    private final Logger logger = LoggerFactory.getLogger(PointLedgerServiceIntegrationTest.class);

    @Autowired private PointTransactionRepository transactionRepository;
    @Autowired private PointTaskRepository taskRepository;

    @Autowired
    private PointLedgerService walletUpdater;

    @Autowired
    private PointWalletRepository walletRepository;

    private final Long testMemberId = 1L;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();

        PointWallet wallet = PointWallet.builder()
                .memberId(testMemberId)
                .balance(1000L)
                .lastSequenceNum(10L)
                .build();

        walletRepository.save(wallet);
        logger.info("[TEST_SETUP] Wallet initialized for MemberID: {}. Balance: 1000", testMemberId);
    }

    @Test
    @DisplayName("비관적 락 검증: 동시 요청 중 정합성이 맞는 것들은 유실 없이 반영되어야 한다")
    void synchronizeWalletFromLedger_Concurrency_Test() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        // when
        for (int i = 1; i <= threadCount; i++) {
            final long seqNum = 10L + i;
            executorService.submit(() -> {
                try {
                    PointTransaction tx = transactionRepository.save(PointTransaction.builder()
                            .memberId(testMemberId)
                            .amount(100L)
                            .pointKey("CONCURRENT-KEY-" + seqNum)
                            .sequenceNum(seqNum)
                            .type(PointTransactionType.EARN)
                            .source(PointSource.SYSTEM)
                            .build());

                    PointTask task = taskRepository.save(PointTask.builder()
                            .transaction(tx)
                            .build());

                    walletUpdater.synchronizeWalletFromLedger(task);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    logger.warn("[TASK_SKIPPED] TaskID: {}, Reason: {}", seqNum, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        PointWallet updatedWallet = walletRepository.findByMemberId(testMemberId).orElseThrow();

        // 성공한 횟수만큼 잔액이 정확히 늘어났는지 확인.
        long expectedBalance = 1000L + (successCount.get() * 100L);

        assertThat(updatedWallet.getBalance()).isEqualTo(expectedBalance);

        logger.info("[INTEGRATION_RESULT] Success: {}, Final Balance: {}",
                successCount.get(), updatedWallet.getBalance());

        // 적어도 한 건 이상은 성공했는지 확인
        assertThat(successCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("롤백 검증: 잔액 부족 시 트랜잭션이 롤백되어 지갑 상태가 유지되어 equator야 한다")
    void synchronizeWalletFromLedger_Rollback_Test() {
        // given
        PointWallet initialWallet = walletRepository.findByMemberId(testMemberId).orElseThrow();
        Long initialBalance = initialWallet.getBalance();

        // 잔액(1000원)보다 큰 금액(5000원) 사용 시도
        PointTransaction tx = PointTransaction.builder()
                .memberId(testMemberId)
                .amount(5000L)
                .pointKey("ROLLBACK-KEY-99")
                .sequenceNum(initialWallet.getLastSequenceNum() + 1)
                .type(PointTransactionType.USE)
                .source(PointSource.ORDER)
                .build();

        PointTask task = PointTask.builder()
                .id(99L)
                .transaction(tx)
                .build();

        // when
        try {
            walletUpdater.synchronizeWalletFromLedger(task);
        } catch (Exception e) {
            logger.info("[EXPECTED_EXCEPTION] Rollback triggered as intended: {}", e.getMessage());
        }

        // then
        PointWallet afterRollbackWallet = walletRepository.findByMemberId(testMemberId).orElseThrow();

        // 롤백 확인: 금액과 시퀀스가 변하지 않아야 함
        assertThat(afterRollbackWallet.getBalance()).isEqualTo(initialBalance);
        assertThat(afterRollbackWallet.getLastSequenceNum()).isEqualTo(initialWallet.getLastSequenceNum());

        logger.info("[TEST_PASSED] Rollback test success. Balance remains: {}", afterRollbackWallet.getBalance());
    }
}