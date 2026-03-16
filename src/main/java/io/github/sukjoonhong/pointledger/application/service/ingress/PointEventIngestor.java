package io.github.sukjoonhong.pointledger.application.service.ingress;

import io.github.sukjoonhong.pointledger.application.service.PointSequenceManager;
import io.github.sukjoonhong.pointledger.application.service.event.PointEventPublisher;
import io.github.sukjoonhong.pointledger.application.service.event.PointTaskCapturedEvent;
import io.github.sukjoonhong.pointledger.infrastructure.lock.DistributedLockManager;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessageSubscriber;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointEventIngestor implements PointMessageSubscriber {

    private final Logger logger = LoggerFactory.getLogger(PointEventIngestor.class);
    private final PointTransactionRepository transactionRepository;
    private final PointTaskRepository taskRepository;
    private final PointSequenceManager sequenceManager;
    private final PointMessagePublisher messagePublisher;
    private final DistributedLockManager lockManager;
    private final PointEventPublisher eventPublisher;

    @Override
    @Transactional
    public void onMessage(PointCommand command) {
        lockManager.executeWithLock(command.pointKey(), () -> {
            if (transactionRepository.existsByPointKey(command.pointKey())) {
                logger.info("[DB_IDEMPOTENCY_HIT] Already processed in history: {}", command.pointKey());
                return null;
            }

            long taskId = recordLedgerAndTask(command);

            propagate(taskId);
            return null;
        });
    }

    private long recordLedgerAndTask(PointCommand command) {
        PointTransaction transaction = transactionRepository.save(command.toEntity());

        PointTask task = taskRepository.save(PointTask.builder()
                .transaction(transaction)
                .build());

        logger.info("Ledger and Task stored successfully. Key: {}, TransactionId: {}",
                command.pointKey(), transaction.getId());
        return task.getId();
    }

    /**
     * Publishes an internal event for task propagation.
     * The TransactionalEventListener will capture this after the current transaction commits.
     */
    private void propagate(Long taskId) {
        logger.info("[EVENT_PUBLISH] Publishing TaskCapturedEvent for TaskID: {}", taskId);
        eventPublisher.publish(new PointTaskCapturedEvent(taskId));
    }

    /**
     * @deprecated This method is provided for testing convenience (e.g., local REST API calls).
     * It automatically populates the sequenceNum if it's missing.
     * In a production environment (e.g., Kafka consumers), the sequenceNum must be provided
     * by the event producer to ensure strict idempotency and message ordering.
     */
    @Deprecated(since = "2026-03-14")
    public void enqueueEvent(PointCommand command) {
        // Automatically assign sequence number if missing for developer convenience.
        PointCommand processedCommand = sequenceManager.fillSequenceIfEmpty(command);

        messagePublisher.publish(processedCommand);
    }
}