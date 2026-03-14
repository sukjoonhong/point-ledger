package io.github.sukjoonhong.pointledger.service.external;

import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.service.PointLedgerService;
import io.github.sukjoonhong.pointledger.service.event.PointTaskCapturedEvent;
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
public class PointTaskRelayService {

    private final Logger logger = LoggerFactory.getLogger(PointTaskRelayService.class);
    private static final int MAX_RETRY_LIMIT = 3;

    private final PointTaskRepository taskRepository;
    private final PointLedgerService ledgerProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskEvent(PointTaskCapturedEvent event) {
        logger.info("[EVENT_RECEIVED] Initiating task processing. TaskID: {}", event.taskId());
        this.onCaptured(event.taskId());
    }

    private void onCaptured(Long taskId) {
        boolean isProcessable = taskRepository.findProcessableTask(
                taskId,
                List.of(TaskStatus.READY, TaskStatus.FAILED),
                MAX_RETRY_LIMIT
        ).isPresent();

        if (!isProcessable) {
            logger.debug("[TASK_SKIP] Task ID: {} is already processed or ineligible.", taskId);
            return;
        }

        try {
            ledgerProcessor.processBalanceUpdate(taskId);
        } catch (Exception e) {
            checkCriticalAlert(taskId, e);
        }
    }

    private void checkCriticalAlert(Long taskId, Exception e) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (!task.isRetryable(MAX_RETRY_LIMIT)) {
                logger.error("[CRITICAL_ALERT] Max retry reached for Task ID: {}. Manual intervention required. Reason: {}",
                        taskId, e.getMessage());
                // TODO: 실제 운영 환경에서는 alertService.alert(...) 호출
            }
        });
    }
}