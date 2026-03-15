package io.github.sukjoonhong.pointledger.application.service.event;

public interface PointEventSubscriber<T> {
    void onEvent(T event);
    boolean supports(Object event);
}