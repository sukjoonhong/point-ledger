package io.github.sukjoonhong.pointledger.infrastructure.messaging.local;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointDlqPublisher;
import io.github.sukjoonhong.pointledger.subscriber.PointMessageSubscriber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalPointQueueConsumer {
    private final LocalPointQueueManager queueManager;
    private final PointMessageSubscriber subscriber;
    private final PointDlqPublisher dlqPublisher;
    private final Logger logger = LoggerFactory.getLogger(LocalPointQueueConsumer.class);

    @PostConstruct
    public void startConsumer() {
        Thread.ofVirtual().name("local-point-consumer").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                PointCommand command = null;
                try {
                    command = queueManager.take();
                    subscriber.onMessage(command);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (command != null) {
                        logger.error("[PROCESSING_FAILED] Key: {} - Routing to DLQ.", command.pointKey());
                        dlqPublisher.sendToDlq(command, e.getMessage());
                    }
                }
            }
        });
    }
}