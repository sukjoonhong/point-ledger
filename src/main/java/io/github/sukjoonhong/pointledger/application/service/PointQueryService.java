package io.github.sukjoonhong.pointledger.application.service;

import io.github.sukjoonhong.pointledger.domain.dto.PointResponse;
import io.github.sukjoonhong.pointledger.domain.dto.PointTraceDto.*;
import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointUsageDetail;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import io.github.sukjoonhong.pointledger.repository.PointUsageDetailRepository;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final PointWalletRepository walletRepository;
    private final PointTransactionRepository transactionRepository;

    /**
     * 특정 멤버의 현재 잔액 조회
     */
    public PointResponse getBalance(Long memberId) {
        PointWallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "Wallet not found for MemberId: " + memberId));

        return PointResponse.builder()
                .memberId(wallet.getMemberId())
                .balance(wallet.getBalance())
                .type("BALANCE")
                .message("Balance retrieved successfully.")
                .build();
    }

    /**
     * 특정 멤버의 전체 포인트 요약 정보를 조회합니다.
     * 잔액 + 보유한 활성 자산 리스트 + 최근 트랜잭션 10건을 조합합니다.
     */
    public MemberPointSummaryResponse getMemberPointSummary(Long memberId, Pageable pageable) {
        // 1. 지갑 확인
        PointWallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND));

        // 2. 활성 자산 목록
        List<PointAsset> activeAssets = assetRepository
                .findAllByMemberIdAndRemainingAmountGreaterThan(memberId, 0L);

        // 3. 트랜잭션 내역 (페이지네이션 적용)
        Page<PointTransaction> txPage = transactionRepository.findAllByMemberId(memberId, pageable);

        return MemberPointSummaryResponse.builder()
                .memberId(memberId)
                .currentBalance(wallet.getBalance())
                .activeAssetCount(activeAssets.size())
                .activeAssets(activeAssets.stream().map(this::mapToAssetSummary).toList())
                .recentTransactions(txPage.map(this::mapToTxSummary))
                .build();
    }

    /**
     * 특정 적립 포인트(Asset)가 어디어디에 쓰였는지 추적합니다.
     */
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

    /**
     * 특정 주문(Order)에 어떤 포인트 자산들이 투입되었는지 추적합니다.
     */
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

    private MemberPointSummaryResponse.AssetSummary mapToAssetSummary(PointAsset a) {
        return MemberPointSummaryResponse.AssetSummary.builder()
                .assetId(a.getId())
                .pointKey(a.getPointKey())
                .remainingAmount(a.getRemainingAmount())
                .expirationDate(a.getExpirationDate())
                .status(a.getStatus().name())
                .build();
    }

    private MemberPointSummaryResponse.TransactionSummary mapToTxSummary(PointTransaction tx) {
        return MemberPointSummaryResponse.TransactionSummary.builder()
                .pointKey(tx.getPointKey())
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .orderId(tx.getOrderId())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}