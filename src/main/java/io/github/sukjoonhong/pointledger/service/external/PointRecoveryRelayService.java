package io.github.sukjoonhong.pointledger.service.external;

import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import io.github.sukjoonhong.pointledger.service.event.PointWalletRecoveryEvent;
import io.github.sukjoonhong.pointledger.service.replay.PointReplayStrategy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class PointRecoveryRelayService {
    private final Logger logger = LoggerFactory.getLogger(PointRecoveryRelayService.class);
    private final PointTransactionRepository transactionRepository;
    private final PointWalletRepository walletRepository;
    private final PointReplayStrategy defaultStrategy;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRecoveryEvent(PointWalletRecoveryEvent event) {
        logger.info("[RECOVERY_EVENT_RECEIVED] Recovering Wallet for MemberID: {}", event.memberId());
        this.onCaptured(event.memberId(), null);
    }

    /**
     * [CDC_INTENT] In a production environment, this method acts as a consumer
     * that captures events from DB logs (CDC) or a message broker.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCaptured(Long memberId, PointReplayStrategy strategy) {
        try {
            PointReplayStrategy activeStrategy = (strategy != null) ? strategy : defaultStrategy;

            var wallet = walletRepository.findByMemberIdWithLock(memberId)
                    .orElseThrow();

            if (!wallet.isRecovering()) {
                logger.debug("[RECOVERY_SKIP] Wallet is already ACTIVE for MemberID: {}", memberId);
                return;
            }

            final var transactions = transactionRepository.findTransactionsAfterSeq(
                    wallet.getMemberId(),
                    wallet.getLastSequenceNum()
            );

            logger.info("[RECOVERY_PROCESS] Replaying {} missing transactions for MemberID: {}",
                    transactions.size(), memberId);

            activeStrategy.replay(wallet, transactions);

            wallet.activate();
            walletRepository.save(wallet);

            logger.info("[RECOVERY_SUCCESS] Wallet is now ACTIVE for MemberID: {}", memberId);
        } catch (Exception e) {
            logger.error("[RECOVERY_FAILED] Critical failure during recovery for MemberID: {}. Reason: {}",
                    memberId, e.getMessage());
            // TODO: 실패 시 Dead Letter Queue에 넣거나 별도 Alert 발송 로직 추가
        }
    }
}