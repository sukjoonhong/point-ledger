package io.github.sukjoonhong.pointledger.application.service;

import io.github.sukjoonhong.pointledger.application.service.core.PointEarnService;
import io.github.sukjoonhong.pointledger.application.service.core.PointUseService;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
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
    public void route(PointWallet wallet, PointTransaction tx) {
        switch (tx.getType()) {
            case EARN -> earnService.handleEarn(wallet, tx);
            case RE_EARN -> earnService.handleReEarn(wallet, tx);
            case CANCEL_EARN -> earnService.handleCancel(tx);
            case USE -> useService.handleUse(tx);
            case CANCEL_USE -> useService.handleCancel(wallet, tx);
            default -> {
                logger.error("[UNSUPPORTED_TX_TYPE] Type: {}, PointKey: {}", tx.getType(), tx.getPointKey());
                throw new PointLedgerException(PointErrorCode.UNSUPPORTED_TX_TYPE, tx.getType().name());
            }
        }
    }
}