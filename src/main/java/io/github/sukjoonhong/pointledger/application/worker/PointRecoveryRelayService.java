package io.github.sukjoonhong.pointledger.application.worker;

import io.github.sukjoonhong.pointledger.application.service.event.PointEventSubscriber;
import io.github.sukjoonhong.pointledger.application.service.event.PointWalletRecoveryEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("worker & !scheduler")
@Service
@RequiredArgsConstructor
public class PointRecoveryRelayService implements PointEventSubscriber<PointWalletRecoveryEvent> {

    private final Logger logger = LoggerFactory.getLogger(PointRecoveryRelayService.class);
    private final PointRecoveryProcessor recoveryProcessor;

    @Override
    public void onEvent(PointWalletRecoveryEvent event) {
        try {
            logger.info("[RECOVERY_EVENT_RECEIVED] Initiating recovery for MemberID: {}", event.memberId());

            recoveryProcessor.processRecovery(event.memberId(), null);

        } catch (Exception e) {
            logger.error("[RECOVERY_FAILED] Critical failure for MemberID: {}. Reason: {}",
                    event.memberId(), e.getMessage());
            // TODO: 실패 시 Dead Letter Queue 또는 Alert 시스템 연동
        }
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof PointWalletRecoveryEvent;
    }
}