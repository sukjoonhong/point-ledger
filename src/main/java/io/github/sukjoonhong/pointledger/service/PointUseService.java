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
import io.github.sukjoonhong.pointledger.service.external.PointOutboxRelayService;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
    private final PointOutboxRelayService outboxRelayService;
    private final BusinessTimeProvider timeProvider;
    private final ObjectMapper objectMapper;

    private static final Sort DEFAULT_DEDUCTION_SORT =
            Sort.by(Sort.Direction.ASC, "source", "expirationDate", "id");

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

        for (PointAsset asset : availableAssets) {
            if (remainToDeduct <= 0) break;

            long deductAmount = Math.min(asset.getRemainingAmount(), remainToDeduct);
            asset.deduct(deductAmount);
            remainToDeduct -= deductAmount;

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
                    "Insufficient total point balance for deduction. Deficit: " + remainToDeduct);
        }

        assetRepository.saveAll(availableAssets);
        usageDetailRepository.saveAll(details);

        logger.info("Point deduction completed. TxID: {}, OrderID: {}, AssetsUsed: {}",
                tx.getId(), tx.getOrderId(), details.size());
    }

    /**
     * 사용 취소 처리
     * 정책: 사용 시점의 역순(LIFO)으로 환불하여 고객에게 유리한 자산(만료일이 긴 것)부터 복구함
     */
    @Transactional
    public void handleCancel(PointWallet wallet, PointTransaction tx) {
        List<PointUsageDetail> usageDetails = usageDetailRepository.findAllForRefund(
                tx.getOrderId(),
                LIFO_REFUND_SORT
        );
        long remainRefundAmount = tx.getAmount();

        for (PointUsageDetail detail : usageDetails) {
            if (remainRefundAmount <= 0) break;

            long refundableFromDetail = detail.getAmountUsed() - detail.getAmountRefunded();
            if (refundableFromDetail <= 0) continue;

            long amountToRefund = Math.min(refundableFromDetail, remainRefundAmount);

            PointAsset asset = assetRepository.findById(detail.getPointAssetId())
                    .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_ACTIVE,
                            "Original asset not found. ID: " + detail.getPointAssetId()));

            if (asset.isExpired(timeProvider)) {
                issueCompensationTransaction(wallet, tx, amountToRefund);
            } else {
                asset.restore(amountToRefund);
                assetRepository.save(asset);
            }

            detail.refund(amountToRefund);
            usageDetailRepository.save(detail);
            remainRefundAmount -= amountToRefund;
        }

        validateRefundResult(tx.getOrderId(), remainRefundAmount);
    }

    private void validateRefundResult(String orderId, long remainAmount) {
        if (remainAmount > 0) {
            logger.error("Refund failed. Amount remaining: {}, OrderID: {}", remainAmount, orderId);
            throw new PointLedgerException(PointErrorCode.INVALID_REFUND_AMOUNT,
                    "Refund amount exceeds available usage details. Remaining: " + remainAmount);
        }
    }

    /**
     * 보상 트랜잭션 발행
     * 원본 자산이 만료되었으므로, 동일한 금액을 새로운 유효기간으로 재발행하기 위해 큐에 던집니다.
     */
    private void issueCompensationTransaction(PointWallet wallet, PointTransaction parentTx, long amount) {
        String newPointKey = "COMP-" + UUID.randomUUID();

        PointCommand command = PointCommand.builder()
                .memberId(wallet.getMemberId())
                .amount(amount)
                .pointKey(newPointKey)
                .type(PointTransactionType.EARN)
                .source(PointSource.SYSTEM)
                .description("Compensation for: " + parentTx.getOrderId())
                .build();

        try {
            PointOutbox outbox = PointOutbox.builder()
                    .eventType("COMPENSATION_EARN")
                    .payload(objectMapper.writeValueAsString(command))
                    .status(PointOutbox.OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxRepository.save(outbox);
            propagate(outbox.getId());
            logger.info("[OUTBOX_SAVED] Compensation task registered for PointKey: {}", newPointKey);
        } catch (JsonProcessingException e) {
            throw new PointLedgerException(PointErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize command");
        }
    }

    private void propagate(Long outboxId) {
        logger.info("[OUTBOX_PUBLISH] Publishing OutboxCapturedEvent for ID: {}", outboxId);
        eventPublisher.publishEvent(new PointOutboxCapturedEvent(outboxId));
    }
}