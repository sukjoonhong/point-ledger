package io.github.sukjoonhong.pointledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PointOutboxRelayService {

    private final Logger logger = LoggerFactory.getLogger(PointOutboxRelayService.class);
    private static final int MAX_RETRY_COUNT = 5;

    private final PointOutboxRepository outboxRepository;
    private final PointMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
//    private final PointAlertService alertService;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<PointOutbox> targets = outboxRepository.findRetryableEvents(
                List.of(PointOutbox.OutboxStatus.PENDING, PointOutbox.OutboxStatus.FAILED),
                MAX_RETRY_COUNT,
                PageRequest.of(0, 50)
        );

        if (targets.isEmpty()) return;

        for (PointOutbox outbox : targets) {
            try {
                processEvent(outbox);
            } catch (Exception e) {
                handleFailure(outbox, e);
            }
        }
    }

    private void processEvent(PointOutbox outbox) throws Exception {
        PointCommand command = objectMapper.readValue(outbox.getPayload(), PointCommand.class);
        messagePublisher.publish(command);
        outbox.complete();

        logger.info("[OUTBOX_RELAY_SUCCESS] ID: {}", outbox.getId());
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