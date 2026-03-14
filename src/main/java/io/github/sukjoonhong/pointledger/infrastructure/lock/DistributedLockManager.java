package io.github.sukjoonhong.pointledger.infrastructure.lock;

import java.util.function.Supplier;

/**
 * Interface for distributed lock management to ensure idempotency across multiple nodes.
 */
public interface DistributedLockManager {

    /**
     * Executes the given action within a distributed lock scope.
     *
     * @param lockKey The unique key for the lock (e.g., pointKey)
     * @param action  The business logic to execute
     * @param <T>     The return type of the action
     * @return The result of the action
     */
    <T> T executeWithLock(String lockKey, Supplier<T> action);
}