package io.github.sukjoonhong.pointledger.infrastructure.messaging.local;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalPointMessagePublisher implements PointMessagePublisher {

    private final LocalPointQueueManager queueManager;

    @Override
    public void publish(PointCommand command) {
        queueManager.offer(command);
    }
}