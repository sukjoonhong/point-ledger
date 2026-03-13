package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
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
                policyManager.getExpiryDays()
        );

        assetRepository.save(asset);

        logger.info("Point asset created successfully. AssetID: {}, TxID: {}, Amount: {}",
                asset.getId(), tx.getId(), tx.getAmount());
    }

    /**
     * 적립 취소 처리 (원천 무효화 - 사용 여부 검증 포함)
     */
    @Transactional
    public void handleCancel(PointTransaction tx) {
        PointAsset asset = assetRepository.findByPointKey(tx.getPointKey())
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "No asset found for pointKey: " + tx.getPointKey()));
        try {
            asset.cancel();
            assetRepository.save(asset);

            logger.info("Earning asset cancelled. AssetID: {}, PointKey: {}",
                    asset.getId(), tx.getPointKey());
        } catch (IllegalStateException e) {
            logger.error("Failed to cancel earning asset. Reason: {}", e.getMessage());
            throw e;
        }
    }
}