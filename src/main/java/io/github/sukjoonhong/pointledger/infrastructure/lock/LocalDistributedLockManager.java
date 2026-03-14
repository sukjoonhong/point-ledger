package io.github.sukjoonhong.pointledger.infrastructure.lock;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * [IMPORTANT] This is a local implementation of DistributedLockManager.
 * It uses Caffeine cache to mimic distributed locking and idempotency checks.
 * * PURPOSE:
 * 1. To prevent concurrent processing of the same pointKey in a single-node environment.
 * 2. To serve as a temporary placeholder before migrating to a real distributed lock (e.g., Redis/Redisson).
 * 3. To provide L1 (local) idempotency filtering to reduce downstream load.
 */
@Component
@RequiredArgsConstructor
public class LocalDistributedLockManager implements DistributedLockManager {

    private final Logger logger = LoggerFactory.getLogger(LocalDistributedLockManager.class);

    /**
     * Cache to store processed or in-flight keys.
     * Boolean value is just a placeholder to indicate the presence of the key.
     */
    private final Cache<String, Boolean> idempotencyCache;

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        // 1. Check if the key is already being processed or has been processed (Idempotency check)
        if (Boolean.TRUE.equals(idempotencyCache.getIfPresent(lockKey))) {
            logger.warn("[LOCK_IMITATION_FAIL] Key already exists in local cache. Potential duplicate or concurrent request: {}", lockKey);

            // Mimic the behavior of a failed lock acquisition or a 409 Conflict scenario
            throw new PointLedgerException(PointErrorCode.CONCURRENT_REQUEST,
                    "Request with this key is already processed or in progress: " + lockKey);
        }

        try {
            // 2. Occupy the lock (Put key into cache to 'lock' it)
            idempotencyCache.put(lockKey, true);
            logger.info("[LOCK_IMITATION_ACQUIRED] Successfully locked key in local cache: {}", lockKey);

            // 3. Execute the core business logic
            return action.get();

        } catch (Exception e) {
            // If an error occurs, we might want to invalidate the key so it can be retried.
            // However, for strict idempotency, we often keep it. Here we follow a 'fail-safe' approach.
            idempotencyCache.invalidate(lockKey);
            logger.error("[LOCK_IMITATION_RELEASED_ON_ERROR] Invalidated lock key due to error: {}", lockKey);
            throw e;
        }
        // Note: For pure lock imitation, we would invalidate the key in a 'finally' block.
        // But for idempotency (remembering), we leave it in the cache until it expires (TTL).
    }
}