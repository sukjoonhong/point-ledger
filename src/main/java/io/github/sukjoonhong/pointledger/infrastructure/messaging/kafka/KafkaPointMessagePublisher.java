package io.github.sukjoonhong.pointledger.infrastructure.messaging.kafka;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
// import org.springframework.kafka.core.KafkaTemplate;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class KafkaPointMessagePublisher implements PointMessagePublisher {

    // private final KafkaTemplate<String, PointCommand> kafkaTemplate;

    @Override
    public void publish(PointCommand command) {
        // kafkaTemplate.send("point-events-topic", command.pointKey(), command);
    }
}