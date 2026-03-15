package io.github.sukjoonhong.pointledger.application.service;

import io.github.sukjoonhong.pointledger.domain.dto.PointTraceDto.*;
import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointUsageDetail;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.repository.PointUsageDetailRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointQueryService {

    private final Logger logger = LoggerFactory.getLogger(PointQueryService.class);
    private final PointAssetRepository assetRepository;
    private final PointUsageDetailRepository usageDetailRepository;

    public AssetTraceResponse traceAssetUsage(Long assetId) {
        PointAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "Asset not found for tracking. ID: " + assetId));

        List<UsageHistory> usages = usageDetailRepository.findAllByPointAssetId(assetId).stream()
                .map(detail -> UsageHistory.builder()
                        .orderId(detail.getOrderId())
                        .amountUsed(detail.getAmountUsed())
                        .usedAt(detail.getCreatedAt())
                        .build())
                .toList();

        return AssetTraceResponse.builder()
                .assetId(asset.getId())
                .source(asset.getSource())
                .totalAmount(asset.getAmount())
                .remainingAmount(asset.getRemainingAmount())
                .usages(usages)
                .build();
    }

    public OrderUsageResponse getOrderUsageTracing(String orderId) {
        List<PointUsageDetail> details = usageDetailRepository.findAllByOrderId(orderId);

        if (details.isEmpty()) {
            logger.warn("No usage details found for OrderID: {}", orderId);
        }

        List<Long> assetIds = details.stream()
                .map(PointUsageDetail::getPointAssetId)
                .distinct()
                .toList();

        Map<Long, PointAsset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(PointAsset::getId, a -> a));

        List<UsedAssetDetail> assetDetails = details.stream()
                .map(d -> {
                    PointAsset asset = assetMap.get(d.getPointAssetId());
                    return UsedAssetDetail.builder()
                            .assetId(d.getPointAssetId())
                            .source(asset != null ? asset.getSource() : null)
                            .amountFromThisAsset(d.getAmountUsed())
                            .build();
                }).toList();

        return OrderUsageResponse.builder()
                .orderId(orderId)
                .totalUsedAmount(details.stream().mapToLong(PointUsageDetail::getAmountUsed).sum())
                .details(assetDetails)
                .build();
    }
}