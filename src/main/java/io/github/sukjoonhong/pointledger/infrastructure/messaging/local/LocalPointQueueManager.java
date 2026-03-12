package io.github.sukjoonhong.pointledger.infrastructure.messaging.local;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class LocalPointQueueManager {
    private final BlockingQueue<PointCommand> queue = new LinkedBlockingQueue<>();

    public void offer(PointCommand command) {
        queue.offer(command);
    }

    public PointCommand take() throws InterruptedException {
        return queue.take();
    }
}