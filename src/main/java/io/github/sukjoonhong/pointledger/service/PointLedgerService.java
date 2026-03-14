package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSequenceStatus;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
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
    private final PointTaskRepository taskRepository;
    private final PointBusinessRouter businessRouter;
    private final PointSequenceValidator sequenceValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final PointPolicyManager policyManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBalanceUpdate(Long taskId) {
        PointTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.TASK_NOT_FOUND,
                        "Task not found for ID: " + taskId));

        if (task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }

        final PointTransaction tx = task.getTransaction();

        try {
            PointWallet wallet = getOrCreateWalletWithLock(tx.getMemberId());

            if (wallet.isRecovering()) {
                logger.warn("[WALLET_BUSY] Wallet is currently recovering. MemberID: {}", wallet.getMemberId());
                throw new PointLedgerException(PointErrorCode.WALLET_UNDER_RECOVERY, "Wallet recovery in progress.");
            }

            PointSequenceStatus status = sequenceValidator.validate(wallet.getLastSequenceNum(), tx.getSequenceNum());
            switch (status) {
                case ALREADY_PROCESSED -> {
                    logger.warn("[INGEST_SKIPPED] Already processed. Seq: {}", tx.getSequenceNum());
                    task.complete();
                    return;
                }
                case GAP_DETECTED -> {
                    logger.info("[SEQUENCE_GAP] Gap detected. Initiating recovery for MemberID: {}", tx.getMemberId());
                    wallet.markAsRecovering();
                    walletRepository.save(wallet);
                    propagate(tx.getMemberId());
                    throw new PointLedgerException(PointErrorCode.WALLET_UNDER_RECOVERY, "Gap detected.");
                }
            }

            // 비즈니스 로직 실행 및 실질 반영 금액 수신
            Long actualAppliedAmount = businessRouter.executeAndGetAppliedAmount(wallet, tx);
            tx.recordAppliedAmount(actualAppliedAmount);

            wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
            walletRepository.save(wallet);

            task.complete();

            logger.info("[TASK_SUCCESS] TaskID: {}, Key: {}, NewBalance: {}",
                    task.getId(), tx.getPointKey(), wallet.getBalance());

        } catch (Exception e) {
            task.fail(e.getMessage());
            logger.error("[TASK_FAILED] TaskID: {}, Error: {}", task.getId(), e.getMessage());
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
            logger.warn("[WALLET_UPSERT_CONFLICT] Concurrent creation for member: {}. Retrying.", memberId);
            return walletRepository.findByMemberIdWithLock(memberId)
                    .orElseThrow(() -> new PointLedgerException(PointErrorCode.INTERNAL_SERVER_ERROR,
                            "Critical error during wallet upsert."));
        }
    }

    private void propagate(Long memberId) {
        eventPublisher.publishEvent(new PointWalletRecoveryEvent(memberId));
    }
}