package io.github.sukjoonhong.pointledger.service.replay;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.service.PointEarnService;
import io.github.sukjoonhong.pointledger.service.PointUseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SequentialReplayStrategy implements PointReplayStrategy {

    private final Logger logger = LoggerFactory.getLogger(SequentialReplayStrategy.class);

    private final PointEarnService earnService;
    private final PointUseService useService;
    private final PointPolicyManager policyManager;

    @Override
    public void replay(PointWallet wallet, List<PointTransaction> transactions) {
        transactions.sort(Comparator.comparingLong(PointTransaction::getSequenceNum));

        for (PointTransaction tx : transactions) {
            validateSequence(wallet, tx);
            routeBusinessLogic(wallet, tx);

            wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());

            logger.info("Successfully replayed transaction. SeqNum: {}, Type: {}", tx.getSequenceNum(), tx.getType());
        }
    }

    private void validateSequence(PointWallet wallet, PointTransaction tx) {
        long currentSeq = wallet.getLastSequenceNum();
        long incomingSeq = tx.getSequenceNum();

        // 1. 이미 처리된 시퀀스인지 먼저 확인 (과거 또는 현재)
        if (incomingSeq <= currentSeq) {
            throw new PointLedgerException(PointErrorCode.INVALID_SEQUENCE,
                    "Already processed. Current: " + currentSeq + ", Incoming: " + incomingSeq);
        }

        // 2. 연속성 확인 (중간에 빈 번호가 있는지)
        if (incomingSeq != currentSeq + 1) {
            throw new PointLedgerException(PointErrorCode.SEQUENCE_GAP_DETECTED,
                    "Expected: " + (currentSeq + 1) + ", Actual: " + incomingSeq);
        }
    }

    private void routeBusinessLogic(PointWallet wallet, PointTransaction tx) {
        switch (tx.getType()) {
            case EARN -> earnService.handleEarn(wallet, tx);
            case CANCEL_EARN -> earnService.handleCancel(tx);
            case USE -> useService.handleUse(tx);
            case CANCEL_USE -> useService.handleCancel(wallet, tx);
            default -> {
                logger.error("Unknown transaction type during replay: {}", tx.getType());
                throw new PointLedgerException(PointErrorCode.UNSUPPORTED_TX_TYPE, tx.getType().name());
            }
        }
    }
}