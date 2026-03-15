package io.github.sukjoonhong.pointledger.application.worker;

import io.github.sukjoonhong.pointledger.application.service.event.PointEventSubscriber;
import io.github.sukjoonhong.pointledger.application.service.event.PointTaskCapturedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("worker & !scheduler")
@Component
@RequiredArgsConstructor
public class PointTaskRelayService implements PointEventSubscriber<PointTaskCapturedEvent> {

    private final Logger logger = LoggerFactory.getLogger(PointTaskRelayService.class);
    private final PointTaskProcessor taskProcessor;

    @Override
    public void onEvent(PointTaskCapturedEvent event) {
        logger.info("[TASK_EVENT_RECEIVED] Delegating TaskID: {}", event.taskId());
        taskProcessor.processTask(event.taskId());
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof PointTaskCapturedEvent;
    }
}