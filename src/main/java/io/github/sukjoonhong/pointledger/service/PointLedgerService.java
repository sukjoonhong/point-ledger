package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.subscriber.PointMessageSubscriber;
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
public class PointLedgerService implements PointMessageSubscriber {

    private final Logger logger = LoggerFactory.getLogger(PointLedgerService.class);
    private final PointTransactionRepository transactionRepository;
    private final PointTaskRepository taskRepository;

    @Override
    @Transactional
    public void onMessage(PointCommand command) {
        if (isDuplicateEvent(command.pointKey())) {
            logger.info("Duplicate pointKey skipped: {}", command.pointKey());
            return;
        }

        recordLedgerAndTask(command);
    }

    private boolean isDuplicateEvent(String pointKey) {
        return transactionRepository.existsByPointKey(pointKey);
    }

    private void recordLedgerAndTask(PointCommand command) {
        PointTransaction transaction = transactionRepository.save(command.toEntity());

        taskRepository.save(PointTask.builder()
                .transaction(transaction)
                .build());

        logger.info("Ledger and Task stored successfully. Key: {}, TransactionId: {}",
                command.pointKey(), transaction.getId());
    }
}