package io.github.sukjoonhong.pointledger.application.worker.subsciber;

import io.github.sukjoonhong.pointledger.application.service.event.PointEventSubscriber;
import io.github.sukjoonhong.pointledger.application.service.event.PointOutboxCapturedEvent;
import io.github.sukjoonhong.pointledger.application.worker.PointOutboxRelayService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("worker & !scheduler")
@Component
@RequiredArgsConstructor
public class PointOutboxEventSubscriber implements PointEventSubscriber<PointOutboxCapturedEvent> {

    private final Logger logger = LoggerFactory.getLogger(PointOutboxEventSubscriber.class);
    private final PointOutboxRelayService outboxRelayService;

    @Override
    public void onEvent(PointOutboxCapturedEvent event) {
        logger.info("[OUTBOX_EVENT_RECEIVED] OutboxID: {}", event.outboxId());
        outboxRelayService.relay(event.outboxId());
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof PointOutboxCapturedEvent;
    }
}