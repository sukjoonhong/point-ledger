package io.github.sukjoonhong.pointledger.infrastructure.messaging.kafka;

import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessageSubscriber;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
// import org.springframework.kafka.annotation.KafkaListener;

/**
 * Example implementation of a Kafka message listener.
 * This class demonstrates how an external message broker can be integrated.
 * It acts as an inbound adapter that consumes messages from a Kafka topic
 * and delegates them to the core domain via the PointMessageSubscriber interface.
 */
@Component
@RequiredArgsConstructor
public class KafkaPointMessageListener {

    private final PointMessageSubscriber subscriber;

    // @KafkaListener(topics = "point-events-topic", groupId = "point-ledger-group")
    public void consume(PointCommand command) {
        subscriber.onMessage(command);
    }
}