package io.github.sukjoonhong.pointledger.application.service.event;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SpringEventDispatcher {

    private final Logger logger = LoggerFactory.getLogger(SpringEventDispatcher.class);
    private final List<PointEventSubscriber<?>> subscribers;

    /**
     * Spring의 TransactionalEventListener를 사용하여 커밋 후 비동기로 실행합니다.
     * 이 메서드가 인프라스트럭처와 비즈니스 Subscriber 사이의 브릿지 역할을 합니다.
     */
    @Async
    @SuppressWarnings("unchecked")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(Object event) {
        subscribers.stream()
                .filter(subscriber -> subscriber.supports(event))
                .forEach(subscriber -> {
                    try {
                        ((PointEventSubscriber<Object>) subscriber).onEvent(event);
                    } catch (Exception e) {
                        logger.error("[EVENT_DISPATCH_FAILED] Event: {}, Error: {}",
                                event.getClass().getSimpleName(), e.getMessage());
                    }
                });
    }
}