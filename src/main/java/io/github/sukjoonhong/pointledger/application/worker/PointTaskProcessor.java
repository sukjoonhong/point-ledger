package io.github.sukjoonhong.pointledger.application.worker;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.application.service.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Profile("worker & !scheduler")
@Component
@RequiredArgsConstructor
public class PointTaskProcessor {
    private final Logger logger = LoggerFactory.getLogger(PointTaskProcessor.class);
    private static final int MAX_RETRY_LIMIT = 3;

    private final PointTaskRepository taskRepository;
    private final PointLedgerService ledgerProcessor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTask(Long taskId) {
        PointTask task = taskRepository.findProcessableTask(
                taskId,
                List.of(TaskStatus.READY, TaskStatus.FAILED),
                MAX_RETRY_LIMIT
        ).orElse(null);

        if (task == null) return;

        try {
            ledgerProcessor.processBalanceUpdate(task);
            task.complete();

            logger.info("[TASK_SUCCESS] TaskID: {}, Key: {}",
                    task.getId(), task.getTransaction().getPointKey());
        } catch (Exception e) {
            // 실패 시 상태 업데이트 (Managed 상태이므로 트랜잭션 종료 시 저장됨)
            task.fail(e.getMessage());

            logger.error("[TASK_EXECUTION_FAILED] TaskID: {}, Retry: {}/{}, Reason: {}",
                    task.getId(), task.getRetryCount(), MAX_RETRY_LIMIT, e.getMessage());

            if (!task.isRetryable(MAX_RETRY_LIMIT)) {
                logger.error("[CRITICAL_ALERT] Max retry reached for Task ID: {}. Manual intervention required.", task.getId());
            }
        }
    }
}