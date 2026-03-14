package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PointTaskConsumer {

    private final Logger logger = LoggerFactory.getLogger(PointTaskConsumer.class);
    private static final int MAX_RETRY_LIMIT = 3;

    private final PointTaskRepository taskRepository;
    private final PointLedgerService ledgerProcessor;
//    private final PointAlertService alertService;

    @Scheduled(fixedDelay = 1000)
    public void processTasks() {
        List<PointTask> tasks = taskRepository.findTasksToProcess(
                List.of(TaskStatus.READY, TaskStatus.FAILED),
                MAX_RETRY_LIMIT,
                PageRequest.of(0, 20)
        );

        for (PointTask task : tasks) {
            try {
                ledgerProcessor.processBalanceUpdate(task);
                task.complete();
                logger.info("[TASK_SUCCESS] TaskID: {}, Key: {}", task.getId(), task.getTransaction().getPointKey());

            } catch (Exception e) {
                handleTaskFailure(task, e);
            } finally {
                taskRepository.save(task);
            }
        }
    }

    private void handleTaskFailure(PointTask task, Exception e) {
        task.fail(e.getMessage());

        logger.error("[TASK_EXECUTION_FAILED] TaskID: {}, Retry: {}/{}, Reason: {}",
                task.getId(), task.getRetryCount(), MAX_RETRY_LIMIT, e.getMessage());

        if (!task.isRetryable(MAX_RETRY_LIMIT)) {
            sendCriticalAlert(task, e);
        }
    }

    private void sendCriticalAlert(PointTask task, Exception e) {
        final String alertMessage = String.format(
                """
                        [CRITICAL_TASK_FAILURE]
                        - Task ID: %d
                        - Point Key: %s
                        - Member ID: %d
                        - Error: %s
                        Manual intervention required as max retry limit is reached.""",
                task.getId(),
                task.getTransaction().getPointKey(),
                task.getTransaction().getMemberId(),
                e.getMessage()
        );

//        alertService.alert(alertMessage);
    }
}