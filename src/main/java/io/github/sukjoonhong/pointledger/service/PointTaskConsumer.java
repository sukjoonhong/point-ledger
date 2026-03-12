package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PointTaskConsumer {

    private final Logger logger = LoggerFactory.getLogger(PointTaskConsumer.class);
    private final PointTaskRepository taskRepository;
    private final PointWalletUpdater walletUpdater;

    @Scheduled(fixedDelay = 1000)
    public void processTasks() {
        List<PointTask> tasks = taskRepository.findTop20ByStatusOrderByCreatedAtAsc(TaskStatus.READY);

        for (PointTask task : tasks) {
            try {
                walletUpdater.processBalanceUpdate(task);
                task.complete();
            } catch (Exception e) {
                // Reference pointKey through transaction for logging
                logger.error("Task execution failed. Key: {}", task.getTransaction().getPointKey(), e);
                task.fail();
            }
            taskRepository.save(task);
        }
    }
}