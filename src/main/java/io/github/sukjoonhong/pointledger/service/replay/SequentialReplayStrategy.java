package io.github.sukjoonhong.pointledger.service.replay;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSequenceStatus;
import io.github.sukjoonhong.pointledger.service.PointBusinessRouter;
import io.github.sukjoonhong.pointledger.service.PointSequenceValidator;
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

    private final PointBusinessRouter businessRouter;
    private final PointSequenceValidator sequenceValidator;
    private final PointPolicyManager policyManager;

    @Override
    public void replay(PointWallet wallet, List<PointTransaction> transactions) {
        transactions.sort(Comparator.comparingLong(PointTransaction::getSequenceNum));

        for (PointTransaction tx : transactions) {
            PointSequenceStatus status = sequenceValidator.validate(wallet.getLastSequenceNum(), tx.getSequenceNum());

            switch (status) {
                case EXPECTED -> {
                    businessRouter.executeAndGetAppliedAmount(wallet, tx);
                    wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
                    logger.info("[REPLAY_STEP_SUCCESS] Seq: {}", tx.getSequenceNum());
                }
                case ALREADY_PROCESSED ->
                        throw new PointLedgerException(PointErrorCode.INVALID_SEQUENCE, "Duplicate in replay");
                case GAP_DETECTED ->
                        throw new PointLedgerException(PointErrorCode.SEQUENCE_GAP_DETECTED, "Gap in replay list");
            }
        }
    }
}