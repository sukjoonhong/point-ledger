package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSequenceStatus;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import io.github.sukjoonhong.pointledger.service.event.PointWalletRecoveryEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PointLedgerService {
    private final Logger logger = LoggerFactory.getLogger(PointLedgerService.class);
    private final PointWalletRepository walletRepository;
    private final PointBusinessRouter businessRouter;
    private final PointSequenceValidator sequenceValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final PointPolicyManager policyManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBalanceUpdate(PointTask task) {
        final var tx = task.getTransaction();

        if (task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }

        try {
            // 1. 비관적 락으로 지갑 조회
            PointWallet wallet = getOrCreateWalletWithLock(tx.getMemberId());

            if (wallet.isRecovering()) {
                logger.warn("[WALLET_BUSY] Wallet is currently recovering. MemberID: {}", wallet.getMemberId());
                throw new PointLedgerException(PointErrorCode.WALLET_UNDER_RECOVERY, "Wallet recovery in progress.");
            }

            PointSequenceStatus status = sequenceValidator.validate(wallet.getLastSequenceNum(), tx.getSequenceNum());

            // 2. 시퀀스 검증
            switch (status) {
                case ALREADY_PROCESSED -> {
                    logger.warn("[INGEST_SKIPPED] Already processed. Seq: {}", tx.getSequenceNum());
                    task.complete();
                    return;
                }
                case GAP_DETECTED -> {
                    logger.info("[SEQUENCE_GAP] Gap detected. Initiating recovery event for MemberID: {}", tx.getMemberId());

                    wallet.markAsRecovering();
                    walletRepository.save(wallet);

                    propagate(tx.getMemberId());

                    throw new PointLedgerException(PointErrorCode.WALLET_UNDER_RECOVERY, "Gap detected, recovery initiated.");
                }
                case EXPECTED -> logger.debug("[SEQUENCE_OK] Sequence validated: {}", tx.getSequenceNum());
            }

            // 3. 현재 트랜잭션 비즈니스 로직 처리
            businessRouter.route(wallet, tx);

            // 4. 지갑 상태 최종 반영
            wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
            walletRepository.save(wallet);

            task.complete();

            logger.info("[TASK_SUCCESS] TaskID: {}, Key: {}, NewBalance: {}, NewSeq: {}",
                    task.getId(), tx.getPointKey(), wallet.getBalance(), wallet.getLastSequenceNum());

        } catch (Exception e) {
            task.fail(e.getMessage());

            logger.error("[TASK_FAILED] TaskID: {}, Key: {}, Retry: {}, Error: {}",
                    task.getId(), tx.getPointKey(), task.getRetryCount(), e.getMessage());

            throw e;
        }
    }

    private PointWallet getOrCreateWalletWithLock(Long memberId) {
        try {
            return walletRepository.findByMemberIdWithLock(memberId)
                    .orElseGet(() -> {
                        logger.info("[WALLET_CREATION_ATTEMPT] Creating new wallet for member: {}", memberId);
                        PointWallet newWallet = PointWallet.builder()
                                .memberId(memberId)
                                .balance(0L)
                                .lastSequenceNum(0L)
                                .build();
                        return walletRepository.save(newWallet);
                    });
        } catch (DataIntegrityViolationException e) {
            logger.warn("[WALLET_UPSERT_CONFLICT] Concurrent wallet creation detected for member: {}. Retrying fetch.", memberId);

            return walletRepository.findByMemberIdWithLock(memberId)
                    .orElseThrow(() -> {
                        logger.error("[WALLET_RECOVERY_FAILED] Failed to fetch wallet after conflict for member: {}", memberId);
                        return new PointLedgerException(PointErrorCode.INTERNAL_SERVER_ERROR,
                                "Critical error during wallet upsert recovery.");
                    });
        }
    }

    private void propagate(Long memberId) {
        logger.info("[EVENT_PUBLISH] Publishing PointWalletRecoveryEvent for MemberId: {}", memberId);
        eventPublisher.publishEvent(new PointWalletRecoveryEvent(memberId));
    }
}