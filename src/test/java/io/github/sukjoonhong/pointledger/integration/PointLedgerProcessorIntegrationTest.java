package io.github.sukjoonhong.pointledger.integration;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import io.github.sukjoonhong.pointledger.service.PointLedgerProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PointLedgerProcessorIntegrationTest {

    private final Logger logger = LoggerFactory.getLogger(PointLedgerProcessorIntegrationTest.class);

    @Autowired
    private PointLedgerProcessor walletUpdater;

    @Autowired
    private PointWalletRepository walletRepository;

    private final Long testMemberId = 999L;

    @BeforeEach
    void setUp() {
        PointWallet wallet = PointWallet.builder()
                .memberId(testMemberId)
                .balance(1000L)
                .lastSequenceNum(10L)
                .build();
        walletRepository.save(wallet);
        logger.info("Test environment setup completed. MemberID: {}", testMemberId);
    }

    @AfterEach
    void tearDown() {
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("비관적 락 검증: 동시에 여러 건의 적립 요청이 들어와도 유실 없이 모두 반영되어야 한다")
    void processBalanceUpdate_Concurrency_WithPessimisticLock() throws InterruptedException {
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
                    PointTransaction tx = PointTransaction.builder()
                            .memberId(testMemberId)
                            .amount(100L)
                            .pointKey("CONCURRENT-TEST-" + seqNum)
                            .sequenceNum(seqNum)
                            .type(PointTransactionType.EARN)
                            .source(PointSource.SYSTEM)
                            .build();

                    PointTask task = new PointTask(tx);
                    walletUpdater.processBalanceUpdate(task);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    logger.error("Concurrency test failed during execution. Reason: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        PointWallet updatedWallet = walletRepository.findByMemberIdWithLock(testMemberId).orElseThrow();

        // Initial 1000 + (100 * 10) = 2000
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(updatedWallet.getBalance()).isEqualTo(2000L);
        assertThat(updatedWallet.getLastSequenceNum()).isEqualTo(20L);

        logger.info("Concurrency test passed. Final balance: {}", updatedWallet.getBalance());
    }

    @Test
    @DisplayName("트랜잭션 롤백 검증: 잔액 부족 등 비즈니스 예외 발생 시 지갑 상태는 이전으로 롤백되어야 한다")
    void processBalanceUpdate_Rollback_OnBusinessException() {
        // given
        PointWallet initialWallet = walletRepository.findByMemberIdWithLock(testMemberId).orElseThrow();
        Long initialBalance = initialWallet.getBalance();
        Long initialSeq = initialWallet.getLastSequenceNum();

        // 1000원 밖에 없는데 5000원을 사용하려고 시도 (IllegalArgumentException 발생 예상)
        PointTransaction tx = PointTransaction.builder()
                .memberId(testMemberId)
                .amount(5000L)
                .pointKey("ROLLBACK-TEST-11")
                .sequenceNum(11L)
                .type(PointTransactionType.USE)
                .orderId("ORDER-999")
                .source(PointSource.ORDER)
                .build();

        PointTask task = new PointTask(tx);

        // when
        try {
            walletUpdater.processBalanceUpdate(task);
        } catch (IllegalArgumentException e) {
            logger.info("Expected exception caught during rollback test. Message: {}", e.getMessage());
        }

        // then
        PointWallet afterRollbackWallet = walletRepository.findByMemberIdWithLock(testMemberId).orElseThrow();

        // Rollback ensures no partial updates occurred
        assertThat(afterRollbackWallet.getBalance()).isEqualTo(initialBalance);
        assertThat(afterRollbackWallet.getLastSequenceNum()).isEqualTo(initialSeq);

        logger.info("Rollback test passed. Balance remained: {}", afterRollbackWallet.getBalance());
    }
}
