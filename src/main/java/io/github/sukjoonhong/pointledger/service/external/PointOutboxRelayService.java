package io.github.sukjoonhong.pointledger.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import io.github.sukjoonhong.pointledger.service.event.PointOutboxCapturedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PointOutboxRelayService {
    private final Logger logger = LoggerFactory.getLogger(PointOutboxRelayService.class);
    private static final int MAX_RETRY_COUNT = 5;

    private final PointOutboxRepository outboxRepository;
    private final PointMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
//    private final PointAlertService alertService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void propagate(PointOutboxCapturedEvent event) {
        logger.info("[OUTBOX_EVENT_RECEIVED] Commited OutboxID: {}", event.outboxId());
        this.onCaptured(event.outboxId());
    }

    /**
     * [CDC_INTENT] In a production environment, this method acts as a consumer
     * that captures events from DB logs (CDC) or a message broker.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void onCaptured(Long outboxId) {
        PointOutbox outbox = outboxRepository.findById(outboxId)
                .filter(o -> o.getStatus() != PointOutbox.OutboxStatus.PROCESSED)
                .filter(o -> o.getRetryCount() < MAX_RETRY_COUNT)
                .orElse(null);

        if (outbox == null) return;

        try {
            PointCommand command = objectMapper.readValue(outbox.getPayload(), PointCommand.class);
            messagePublisher.publish(command);
            outbox.complete();
            logger.info("[OUTBOX_RELAY_SUCCESS] OutboxID: {} has been published.", outboxId);
        } catch (Exception e) {
            handleFailure(outbox, e);
        } finally {
            outboxRepository.save(outbox);

            if (outbox.getStatus() != PointOutbox.OutboxStatus.PROCESSED
                    && outbox.getRetryCount() < MAX_RETRY_COUNT) {
                logger.warn("[OUTBOX_RELIABILITY_RETRY] Re-publishing event for OutboxID: {} to ensure eventual consistency. Attempt: {}/{}",
                        outboxId, outbox.getRetryCount(), MAX_RETRY_COUNT);

                eventPublisher.publishEvent(new PointOutboxCapturedEvent(outboxId));
            }
        }
    }

    private void handleFailure(PointOutbox outbox, Exception e) {
        outbox.fail();
        if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
            sendCriticalAlert(outbox, e);
        } else {
            logger.warn("[OUTBOX_RELAY_RETRYING] ID: {}, Count: {}/{}",
                    outbox.getId(), outbox.getRetryCount(), MAX_RETRY_COUNT);
        }
    }

    private void sendCriticalAlert(PointOutbox outbox, Exception e) {
        final String message = String.format(
                """
                        [CRITICAL_OUTBOX_FAILURE]
                        - Outbox ID: %d
                        - Retry Count: %d
                        - Error Message: %s
                        - Payload: %s
                        Manual intervention required immediately.""",
                outbox.getId(),
                outbox.getRetryCount(),
                e.getMessage(),
                outbox.getPayload()
        );

//        alertService.alert(message);

        logger.error("[ALERT_SENT] Critical failure alert has been dispatched for Outbox ID: {}", outbox.getId());
    }
}