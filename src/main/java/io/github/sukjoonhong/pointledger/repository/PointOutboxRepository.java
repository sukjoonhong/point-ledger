package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PointOutboxRepository extends JpaRepository<PointOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2") // SKIP LOCKED
    })
    @Query("""
                SELECT o FROM PointOutbox o 
                WHERE o.status IN :statuses 
                  AND o.retryCount < :maxRetryCount 
                ORDER BY o.createdAt ASC
            """)
    List<PointOutbox> findRetryableEvents(
            @Param("statuses") Collection<PointOutbox.OutboxStatus> statuses,
            @Param("maxRetryCount") int maxRetryCount,
            Pageable pageable
    );

    List<PointOutbox> findAllByStatus(PointOutbox.OutboxStatus status);
}
