package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
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
public class PointLedgerProcessor {

    private final Logger logger = LoggerFactory.getLogger(PointLedgerProcessor.class);
    private final PointWalletRepository walletRepository;
    private final PointEarnService earnService;
    private final PointUseService useService;
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

            final long currentSeq = tx.getSequenceNum();
            final long lastSeq = wallet.getLastSequenceNum();

            // 2. 시퀀스 검증
            if (currentSeq <= lastSeq) {
                logger.warn("[ALREADY_PROCESSED] TaskID: {}, Key: {}, Seq: {}", task.getId(), tx.getPointKey(), currentSeq);
                task.complete(); // 이미 처리된 건이므로 완료 처리
                return;
            }

            // 시퀀스 갭 발생 시 리플레이 수행
            if (currentSeq > lastSeq + 1) {
                logger.info("[GAP_DETECTED] TaskID: {}, Expected: {}, Incoming: {}", task.getId(), lastSeq + 1, currentSeq);
                replayService.performReplay(wallet, null);

                if (currentSeq <= wallet.getLastSequenceNum()) {
                    task.complete();
                    return;
                }
            }

            // 3. 현재 트랜잭션 비즈니스 로직 처리
            executeBusinessLogic(wallet, tx);

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

    /**
     * 타입별 비즈니스 로직 분리 (리플레이에서도 호출할 수 있도록 모듈화)
     */
    public void executeBusinessLogic(PointWallet wallet, PointTransaction tx) {
        switch (tx.getType()) {
            case EARN -> earnService.handleEarn(wallet, tx);
            case CANCEL_EARN -> earnService.handleCancel(tx);
            case USE -> useService.handleUse(tx);
            case CANCEL_USE -> useService.handleCancel(wallet, tx);
        }
    }
}