package io.github.sukjoonhong.pointledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.*;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.domain.type.PointUsageStatus;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import io.github.sukjoonhong.pointledger.repository.PointUsageDetailRepository;
import io.github.sukjoonhong.pointledger.service.event.PointOutboxCapturedEvent;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointUseService {

    private final Logger logger = LoggerFactory.getLogger(PointUseService.class);
    private final PointAssetRepository assetRepository;
    private final PointUsageDetailRepository usageDetailRepository;
    private final PointOutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BusinessTimeProvider timeProvider;
    private final ObjectMapper objectMapper;

    private static final Sort DEFAULT_DEDUCTION_SORT =
            Sort.by(Sort.Direction.ASC, "sourcePriority", "expirationDate", "id");

    private static final Sort LIFO_REFUND_SORT =
            Sort.by(Sort.Direction.DESC, "id");

    @Transactional
    public void handleUse(PointTransaction tx) {
        List<PointAsset> availableAssets = assetRepository.findAllForDeduction(
                tx.getMemberId(),
                DEFAULT_DEDUCTION_SORT
        );

        long remainToDeduct = tx.getAmount();
        List<PointUsageDetail> details = new ArrayList<>();
        List<PointAsset> modifiedAssets = new ArrayList<>();

        for (PointAsset asset : availableAssets) {
            if (remainToDeduct <= 0) break;

            long deductAmount = Math.min(asset.getRemainingAmount(), remainToDeduct);
            asset.deduct(deductAmount);
            remainToDeduct -= deductAmount;

            modifiedAssets.add(asset);
            details.add(PointUsageDetail.builder()
                    .transactionId(tx.getId())
                    .pointAssetId(asset.getId())
                    .orderId(tx.getOrderId())
                    .amountUsed(deductAmount)
                    .amountRefunded(0L)
                    .status(PointUsageStatus.USED)
                    .build());
        }

        if (remainToDeduct > 0) {
            throw new PointLedgerException(PointErrorCode.INSUFFICIENT_BALANCE,
                    "Insufficient balance. Deficit: " + remainToDeduct);
        }

        assetRepository.saveAll(modifiedAssets);
        usageDetailRepository.saveAll(details);

        logger.info("Point deduction successful. TxID: {}, Amount: {}", tx.getId(), tx.getAmount());
    }

    /**
     * 사용 취소 처리
     * - 도메인 규칙: 1주문(orderId) 당 포인트 사용(USE)은 1회만 발생함을 전제함.
     * - 부분 취소 발생 시, 동일 orderId로 묶인 UsageDetail을 LIFO 순서로 탐색하여 미환불 잔여액(refundable)만큼 복구함.
     */
    @Transactional
    public void handleCancel(PointWallet wallet, PointTransaction tx) {
        List<PointUsageDetail> usageDetails = usageDetailRepository.findAllForRefund(
                tx.getOrderId(),
                LIFO_REFUND_SORT
        );

        long remainRefundAmount = tx.getAmount();
        List<PointAsset> assetsToUpdate = new ArrayList<>();

        for (PointUsageDetail detail : usageDetails) {
            if (remainRefundAmount <= 0) break;

            long refundable = detail.getAmountUsed() - detail.getAmountRefunded();
            if (refundable <= 0) continue;

            long amountToRefund = Math.min(refundable, remainRefundAmount);
            PointAsset asset = assetRepository.findById(detail.getPointAssetId())
                    .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_ACTIVE,
                            "Original asset not found. ID: " + detail.getPointAssetId()));

            if (asset.isExpired(timeProvider)) {
                issueReEarnTransaction(wallet, tx, amountToRefund);
            } else {
                asset.restore(amountToRefund);
                assetsToUpdate.add(asset);
            }

            detail.refund(amountToRefund);
            remainRefundAmount -= amountToRefund;
        }

        assetRepository.saveAll(assetsToUpdate);
        usageDetailRepository.saveAll(usageDetails);

        validateRefundResult(tx.getOrderId(), remainRefundAmount);
    }

    private void issueReEarnTransaction(PointWallet wallet, PointTransaction tx, long amount) {
        String newKey = "RE-" + UUID.randomUUID().toString().substring(0, 8);
        PointCommand command = PointCommand.builder()
                .memberId(wallet.getMemberId())
                .amount(amount)
                .pointKey(newKey)
                .type(PointTransactionType.RE_EARN)
                .source(PointSource.SYSTEM)
                .description("Compensation for expired asset. Original OrderID: " + tx.getOrderId())
                .build();

        try {
            PointOutbox outbox = PointOutbox.builder()
                    .eventType("RE_EARN")
                    .payload(objectMapper.writeValueAsString(command))
                    .status(PointOutbox.OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxRepository.save(outbox);
            propagate(outbox.getId());
        } catch (JsonProcessingException e) {
            throw new PointLedgerException(PointErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize RE_EARN command");
        }
    }

    private void validateRefundResult(String orderId, long remainAmount) {
        if (remainAmount > 0) {
            logger.error("Refund failed. Amount remaining: {}, OrderID: {}", remainAmount, orderId);
            throw new PointLedgerException(PointErrorCode.INVALID_REFUND_AMOUNT,
                    "Refund amount exceeds available usage details. Remaining: " + remainAmount);
        }
    }

    private void propagate(Long outboxId) {
        logger.info("[OUTBOX_PUBLISH] Publishing OutboxCapturedEvent for ID: {}", outboxId);
        eventPublisher.publishEvent(new PointOutboxCapturedEvent(outboxId));
    }
}