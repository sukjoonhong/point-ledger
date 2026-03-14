package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointBusinessRouter {

    private final Logger logger = LoggerFactory.getLogger(PointBusinessRouter.class);
    private final PointEarnService earnService;
    private final PointUseService useService;

    /**
     * 트랜잭션 타입에 따라 적절한 비즈니스 핸들러로 라우팅합니다.
     */
    @Transactional
    public Long executeAndGetAppliedAmount(PointWallet wallet, PointTransaction tx) {
        switch (tx.getType()) {
            case EARN -> {
                earnService.handleEarn(wallet, tx);
                return tx.getAmount();
            }
            case RE_EARN -> {
                earnService.handleReEarn(wallet, tx);
                return tx.getAmount();
            }
            case CANCEL_EARN -> {
                earnService.handleCancel(tx);
                return tx.getAmount();
            }
            case USE -> {
                useService.handleUse(tx);
                return tx.getAmount();
            }
            case CANCEL_USE -> {
                return useService.cancelAndGetRestoredAmount(wallet, tx);
            }
            default -> {
                logger.error("[UNSUPPORTED_TX_TYPE] Type: {}, PointKey: {}", tx.getType(), tx.getPointKey());
                throw new PointLedgerException(PointErrorCode.UNSUPPORTED_TX_TYPE, tx.getType().name());
            }
        }
    }
}