package io.github.sukjoonhong.pointledger.application.worker;

import io.github.sukjoonhong.pointledger.application.service.event.PointEventSubscriber;
import io.github.sukjoonhong.pointledger.application.service.event.PointOutboxCapturedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("worker & !scheduler")
@Component
@RequiredArgsConstructor
public class PointOutboxRelayService implements PointEventSubscriber<PointOutboxCapturedEvent> {

    private final Logger logger = LoggerFactory.getLogger(PointOutboxRelayService.class);
    private final PointOutboxProcessor outboxProcessor;

    @Override
    public void onEvent(PointOutboxCapturedEvent event) {
        logger.info("[OUTBOX_EVENT_RECEIVED] OutboxID: {}", event.outboxId());
        outboxProcessor.processOutbox(event.outboxId());
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof PointOutboxCapturedEvent;
    }
}