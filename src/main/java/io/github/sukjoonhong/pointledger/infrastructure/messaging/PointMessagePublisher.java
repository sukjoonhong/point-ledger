package io.github.sukjoonhong.pointledger.infrastructure.messaging;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;

/**
 * 메시지 브로커로 이벤트를 발행하기 위한 인터페이스.
 * 구현체에 따라 Local Queue 또는 Kafka로 메시지를 전달합니다.
 */
public interface PointMessagePublisher {
    void publish(PointCommand command);
}