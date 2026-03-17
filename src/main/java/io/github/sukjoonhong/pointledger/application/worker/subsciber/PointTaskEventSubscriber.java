package io.github.sukjoonhong.pointledger.application.worker.subsciber;

import io.github.sukjoonhong.pointledger.application.service.event.PointEventSubscriber;
import io.github.sukjoonhong.pointledger.application.service.event.PointTaskCapturedEvent;
import io.github.sukjoonhong.pointledger.application.worker.PointTaskExecutor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("worker")
@Component
@RequiredArgsConstructor
public class PointTaskEventSubscriber implements PointEventSubscriber<PointTaskCapturedEvent> {

    private final Logger logger = LoggerFactory.getLogger(PointTaskEventSubscriber.class);
    private final PointTaskExecutor taskProcessor;

    @Override
    public void onEvent(PointTaskCapturedEvent event) {
        logger.info("[TASK_EVENT_RECEIVED] Delegating TaskID: {}", event.taskId());
        taskProcessor.execute(event.taskId());
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof PointTaskCapturedEvent;
    }
}