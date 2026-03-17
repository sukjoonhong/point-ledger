package io.github.sukjoonhong.pointledger.application.worker;

import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import io.github.sukjoonhong.pointledger.application.service.replay.PointReplayStrategy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Profile("worker")
@Component
@RequiredArgsConstructor
public class PointWalletStateRestorer {
    private final Logger logger = LoggerFactory.getLogger(PointWalletStateRestorer.class);
    private final PointTransactionRepository transactionRepository;
    private final PointWalletRepository walletRepository;
    private final PointReplayStrategy defaultStrategy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restore(Long memberId, PointReplayStrategy strategy) {
        PointReplayStrategy activeStrategy = (strategy != null) ? strategy : defaultStrategy;

        // 1. 비관적 락으로 지갑 점유
        var wallet = walletRepository.findByMemberIdWithLock(memberId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for member: " + memberId));

        if (!wallet.isRecovering()) {
            logger.debug("[RECOVERY_SKIP] Wallet is already ACTIVE for MemberID: {}", memberId);
            return;
        }

        // 2. 누락된 시퀀스 이후의 트랜잭션 조회
        final var transactions = transactionRepository.findTransactionsAfterSeq(
                wallet.getMemberId(),
                wallet.getLastSequenceNum()
        );

        logger.info("[RECOVERY_PROCESS] Replaying {} missing transactions for MemberID: {}",
                transactions.size(), memberId);

        // 3. 재처리 전략 실행 (지갑 잔액 및 시퀀스 복구)
        activeStrategy.replay(wallet, transactions);

        // 4. 상태 복구 및 저장
        wallet.activate();
        walletRepository.save(wallet);

        logger.info("[RECOVERY_SUCCESS] Wallet is now ACTIVE for MemberID: {}", memberId);
    }
}