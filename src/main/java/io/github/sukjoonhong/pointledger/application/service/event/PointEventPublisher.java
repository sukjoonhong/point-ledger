package io.github.sukjoonhong.pointledger.application.service.event;

/**
 * 포인트 시스템의 이벤트를 발행하는 추상 인터페이스입니다.
 * 인프라스트럭처의 변화(Spring Event -> Kafka 등)에 관계없이 비즈니스 로직을 보호합니다.
 */
public interface PointEventPublisher {
    void publish(Object event);
}