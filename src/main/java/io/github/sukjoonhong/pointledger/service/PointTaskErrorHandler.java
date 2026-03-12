package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PointTaskErrorHandler {
    private final Logger logger = LoggerFactory.getLogger(PointTaskErrorHandler.class);

    /**
     * Handles lock acquisition failure.
     */
    public void handleLockFailure(PointTask task, String errorMessage) {
        logger.warn("[LOCK_TIMEOUT] Transaction ID: {} - Failed to acquire DB lock. Will retry in next cycle. Reason: {}",
                task.getTransaction().getId(),
                errorMessage);
    }

    /**
     * Handles general processing failure.
     */
    public void handleUpdateFailure(PointTask task, String errorMessage) {
        logger.error("[TASK_FAILED] Transaction ID: {} - Execution failed. Reason: {}",
                task.getTransaction().getId(),
                errorMessage);
    }
}