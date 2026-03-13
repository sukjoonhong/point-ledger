package io.github.sukjoonhong.pointledger.infrastructure.messaging.local;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointDlqPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalPointDlqPublisher implements PointDlqPublisher {

    private final Logger logger = LoggerFactory.getLogger(LocalPointDlqPublisher.class);

    @Override
    public void sendToDlq(PointCommand command, String reason) {
        logger.error("[DLQ_PUBLISHED] Key: {}, Reason: {} - Message routed to DLQ.",
                command.pointKey(), reason);
    }
}