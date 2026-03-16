package io.github.sukjoonhong.pointledger.application.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Profile("worker & !scheduler")
@Component
@RequiredArgsConstructor
public class PointOutboxRelayService {
    private final Logger logger = LoggerFactory.getLogger(PointOutboxRelayService.class);
    private static final int MAX_RETRY_COUNT = 5;

    private final PointOutboxRepository outboxRepository;
    private final PointMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relay(Long outboxId) {
        PointOutbox outbox = outboxRepository.findById(outboxId)
                .filter(o -> o.getStatus() != PointOutbox.OutboxStatus.PROCESSED)
                .filter(o -> o.getRetryCount() < MAX_RETRY_COUNT)
                .orElse(null);

        if (outbox == null) return;

        try {
            PointCommand command = objectMapper.readValue(outbox.getPayload(), PointCommand.class);
            messagePublisher.publish(command);
            outbox.complete();
            logger.info("[OUTBOX_RELAY_SUCCESS] OutboxID: {} published.", outboxId);
        } catch (Exception e) {
            outbox.fail(); // Managed 상태이므로 커밋 시 반영됨
            logger.warn("[OUTBOX_RELAY_FAILED] ID: {}, Count: {}/{}",
                    outbox.getId(), outbox.getRetryCount(), MAX_RETRY_COUNT);

            if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                logger.error("[CRITICAL_OUTBOX_FAILURE] Max retry reached for ID: {}", outboxId);
            }
            // 여기서 예외를 다시 던지지 않거나, 전략에 따라 처리
        }
    }
}