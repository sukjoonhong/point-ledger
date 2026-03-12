package io.github.sukjoonhong.pointledger.subscriber;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;

/**
 * 외부 메시지(Kafka, Local Queue 등)를 수신하기 위한 인터페이스.
 */
public interface PointMessageSubscriber {
    void onMessage(PointCommand command);
}