package io.github.sukjoonhong.pointledger.application.worker;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.application.service.core.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Profile("worker")
@Component
@RequiredArgsConstructor
public class PointTaskExecutor {
    private final Logger logger = LoggerFactory.getLogger(PointTaskExecutor.class);
    private static final int MAX_RETRY_LIMIT = 3;

    private final PointTaskRepository taskRepository;
    private final PointLedgerService ledgerService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Long taskId) {
        PointTask task = taskRepository.findProcessableTask(
                taskId,
                List.of(TaskStatus.READY, TaskStatus.FAILED),
                MAX_RETRY_LIMIT
        ).orElse(null);

        if (task == null || task.getStatus() == TaskStatus.COMPLETED) return;

        try {
            ledgerService.synchronizeWalletFromLedger(task.getTransaction());
            task.complete();

            logger.info("[TASK_SUCCESS] TaskID: {}, Key: {}",
                    task.getId(), task.getTransaction().getPointKey());
        } catch (Exception e) {
            task.fail(e.getMessage());

            logger.error("[TASK_EXECUTION_FAILED] TaskID: {}, Retry: {}/{}, Reason: {}",
                    task.getId(), task.getRetryCount(), MAX_RETRY_LIMIT, e.getMessage());

            if (!task.isRetryable(MAX_RETRY_LIMIT)) {
                logger.error("[CRITICAL_ALERT] Max retry reached for Task ID: {}. Manual intervention required.", task.getId());
            }
        }
    }
}