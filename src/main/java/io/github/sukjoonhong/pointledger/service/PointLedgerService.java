package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.type.PointSequenceStatus;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import io.github.sukjoonhong.pointledger.service.replay.PointReplayService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final PointReplayService replayService;
    private final PointPolicyManager policyManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBalanceUpdate(PointTask task) {
        final var tx = task.getTransaction();

        if (task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }

        try {
            // 1. 비관적 락으로 지갑 조회
            final var wallet = walletRepository.findByMemberIdWithLock(tx.getMemberId())
                    .orElseGet(() -> walletRepository.save(
                            PointWallet.builder()
                                    .memberId(tx.getMemberId())
                                    .balance(0L)
                                    .lastSequenceNum(0L)
                                    .build()
                    ));

            PointSequenceStatus status = sequenceValidator.validate(wallet.getLastSequenceNum(), tx.getSequenceNum());

            // 2. 시퀀스 검증
            switch (status) {
                case ALREADY_PROCESSED -> {
                    logger.warn("[INGEST_SKIPPED] Already processed. TaskID: {}, Seq: {}", task.getId(), tx.getSequenceNum());
                    task.complete();
                    return;
                }
                case GAP_DETECTED -> {
                    logger.info("[SEQUENCE_GAP] Gap detected. TaskID: {}, Replaying...", task.getId());
                    replayService.performReplay(wallet, null);

                    // 리플레이 후 상태 재점검 (이미 처리됐으면 종료)
                    if (sequenceValidator.validate(wallet.getLastSequenceNum(), tx.getSequenceNum())
                            == PointSequenceStatus.ALREADY_PROCESSED) {
                        task.complete();
                        return;
                    }
                }
                case EXPECTED -> logger.debug("[SEQUENCE_OK] Processing expected sequence: {}", tx.getSequenceNum());
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
}