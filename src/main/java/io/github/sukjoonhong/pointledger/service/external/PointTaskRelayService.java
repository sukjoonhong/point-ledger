package io.github.sukjoonhong.pointledger.service.external;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.service.PointLedgerService;
import io.github.sukjoonhong.pointledger.service.event.PointTaskCapturedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PointTaskRelayService {

    private final Logger logger = LoggerFactory.getLogger(PointTaskRelayService.class);
    private static final int MAX_RETRY_LIMIT = 3;

    private final PointTaskRepository taskRepository;
    private final PointLedgerService ledgerProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskEvent(PointTaskCapturedEvent event) {
        logger.info("[EVENT_RECEIVED] After commit! TaskID: {}", event.taskId());
        this.onCaptured(event.taskId());
    }

    /**
     * [CDC_INTENT] In a production environment, this method acts as a consumer
     * that captures events from DB logs (CDC) or a message broker.
     */
    private void onCaptured(Long taskId) {
        PointTask task = taskRepository.findProcessableTask(
                taskId,
                List.of(TaskStatus.READY, TaskStatus.FAILED),
                MAX_RETRY_LIMIT
        ).orElseGet(() -> {
            logger.debug("[TASK_SKIP] Task ID: {} is already processed or not found.", taskId);
            return null;
        });

        if (task == null) return;

        try {
            ledgerProcessor.processBalanceUpdate(task);
            task.complete();
            logger.info("[TASK_SUCCESS] TaskID: {}, Key: {}",
                    task.getId(), task.getTransaction().getPointKey());

        } catch (Exception e) {
            handleTaskFailure(task, e);
        } finally {
            taskRepository.save(task);
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
        logger.error("[CRITICAL_ALERT] Max retry reached for Task ID: {}. Manual intervention required.", task.getId());
        // TODO: 실제 운영 환경에서는 alertService.alert(...) 호출
    }
}