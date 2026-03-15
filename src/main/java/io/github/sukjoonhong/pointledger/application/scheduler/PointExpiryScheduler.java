package io.github.sukjoonhong.pointledger.application.scheduler;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * [ROLE_RESTRICTION]
 * This scheduler runs only in the 'scheduler' profile.
 * It handles the expiration of point assets based on the business time policy.
 */
@Profile("scheduler")
@Component
@RequiredArgsConstructor
public class PointExpiryScheduler {

    private final Logger logger = LoggerFactory.getLogger(PointExpiryScheduler.class);
    private final PointAssetRepository assetRepository;
    private final BusinessTimeProvider timeProvider;

    /**
     * Runs every midnight to process expired assets.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void processExpiredPoints() {
        OffsetDateTime now = timeProvider.nowOffset();
        logger.info("[EXPIRY_BATCH_STARTED] Current Business Time: {}", now);

        try {
            List<PointAsset> targets = assetRepository.findAllByStatusAndExpirationDateBefore(
                    PointAssetStatus.ACTIVE, now);

            if (targets.isEmpty()) {
                logger.info("[EXPIRY_BATCH_COMPLETED] No assets found to expire.");
                return;
            }

            for (PointAsset asset : targets) {
                asset.expire();

                /*
                 * [EVENT_PROPOSAL]
                 * In a production environment, we should trigger an event to sync wallet balance.
                 * pointEventPublisher.publish(new PointExpiredEvent(asset.getMemberId(), asset.getRemainingAmount()));
                 */
            }
            assetRepository.saveAll(targets);
            logger.info("[EXPIRY_BATCH_SUCCESS] Successfully expired {} assets.", targets.size());

        } catch (Exception e) {
            logger.error("[EXPIRY_BATCH_FAILED] Critical error during expiry processing: {}", e.getMessage());
            // Manual intervention required
        }
    }
}