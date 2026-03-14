package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointEarnService {

    private final Logger logger = LoggerFactory.getLogger(PointEarnService.class);
    private final PointAssetRepository assetRepository;
    private final BusinessTimeProvider timeProvider;
    private final PointPolicyManager policyManager;

    /**
     * 신규 포인트 적립 처리 (엔티티를 통한 정책 검증 포함)
     */
    @Transactional
    public void handleEarn(PointWallet wallet, PointTransaction tx) {
        PointAsset asset = PointAsset.createActiveAsset(
                wallet,
                tx,
                policyManager.getMinEarnLimit(),
                policyManager.getMaxEarnLimit(),
                policyManager.getExpireDays(),
                timeProvider
        );

        assetRepository.save(asset);

        logger.info("Point asset created successfully. AssetID: {}, TxID: {}, Amount: {}",
                asset.getId(), tx.getId(), tx.getAmount());
    }

    /**
     * 재발급 포인트 적립 처리
     */
    @Transactional
    public void handleReEarn(PointWallet wallet, PointTransaction tx) {
        PointAsset newAsset = PointAsset.builder()
                .walletId(wallet.getId())
                .memberId(tx.getMemberId())
                .transactionId(tx.getId())
                .pointKey(tx.getPointKey())
                .amount(tx.getAmount())
                .remainingAmount(tx.getAmount())
                .status(PointAssetStatus.ACTIVE)
                .expirationDate(timeProvider.nowOffset().plusDays(policyManager.getExpireDays()))
                .source(tx.getSource())
                .sourcePriority(tx.getSource().getPriority())
                .seqNum(tx.getSequenceNum())
                .build();

        assetRepository.save(newAsset);
        logger.info("[RE_EARN_ASSET_CREATED] New asset generated for expired refund. TxID: {}", tx.getId());
    }

    /**
     * 적립 취소 처리 (원천 무효화 - 사용 여부 검증 포함)
     */
    @Transactional
    public void handleCancel(PointTransaction tx) {
        PointAsset asset = assetRepository.findByPointKey(tx.getOriginalPointKey())
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "No asset found for pointKey: " + tx.getOriginalPointKey()));
        try {
            asset.cancel();
            assetRepository.save(asset);

            logger.info("Earning asset cancelled. AssetID: {}, PointKey: {} OriginalKey: {}",
                    asset.getId(), tx.getPointKey(), tx.getOriginalPointKey());
        } catch (IllegalStateException e) {
            logger.error("Failed to cancel earning asset. Reason: {}", e.getMessage());
            throw e;
        }
    }
}