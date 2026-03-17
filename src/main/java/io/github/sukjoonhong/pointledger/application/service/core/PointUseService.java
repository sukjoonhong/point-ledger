package io.github.sukjoonhong.pointledger.application.service.core;

import io.github.sukjoonhong.pointledger.application.service.PointAssetManager;
import io.github.sukjoonhong.pointledger.application.service.PointOutboxService;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.repository.PointUsageDetailRepository;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointUseService {
    private final Logger logger = LoggerFactory.getLogger(PointUseService.class);
    private final PointAssetRepository assetRepository;
    private final PointUsageDetailRepository usageDetailRepository;
    private final PointAssetManager assetManager;
    private final PointOutboxService outboxService;
    private final BusinessTimeProvider timeProvider;
    private final EntityManager entityManager;

    private static final Sort DEDUCTION_SORT = Sort.by(Sort.Direction.ASC, "sourcePriority", "expirationDate", "id");
    private static final Sort REFUND_SORT = Sort.by(Sort.Direction.DESC, "id");

    @Transactional
    public void handleUse(PointTransaction tx) {
        var availableAssets = assetRepository.findAllForDeduction(tx.getMemberId(), DEDUCTION_SORT);
        var result = assetManager.deduct(tx, availableAssets);

        if (result.remainingDeficit() > 0) {
            throw new PointLedgerException(PointErrorCode.INSUFFICIENT_BALANCE, "Deficit: " + result.remainingDeficit());
        }

        assetRepository.saveAll(result.modifiedAssets());
        usageDetailRepository.saveAll(result.usageDetails());
        logger.info("[USE_SUCCESS] TxID: {}, Amount: {}", tx.getId(), tx.getAmount());
    }

    @Transactional
    public void handleCancel(PointWallet wallet, PointTransaction tx) {
        var usageDetails = usageDetailRepository.findAllForRefund(tx.getOrderId(), REFUND_SORT);

        var refundItems = assetManager.calculateRefund(tx, usageDetails,
                id -> assetRepository.findById(id).orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND)),
                timeProvider);

        long nextSequence = tx.getSequenceNum() + 1;

        for (var item : refundItems) {
            if (item.expired()) {
                outboxService.createReEarnOutbox(wallet, tx, item.amount(), nextSequence++);
            } else {
                item.asset().restore(item.amount());
                assetRepository.save(item.asset());
            }
            item.detail().refund(item.amount());
            usageDetailRepository.save(item.detail());
        }

        validateRefund(tx, refundItems);
    }

    private void validateRefund(PointTransaction tx, List<PointAssetManager.RefundItem> items) {
        long processedAmount = items.stream().mapToLong(PointAssetManager.RefundItem::amount).sum();
        if (processedAmount < tx.getAmount()) {
            throw new PointLedgerException(PointErrorCode.INVALID_REFUND_AMOUNT, "Shortfall: " + (tx.getAmount() - processedAmount));
        }
    }
}