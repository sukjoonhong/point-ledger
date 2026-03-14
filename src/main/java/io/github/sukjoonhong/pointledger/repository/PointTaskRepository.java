package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PointTaskRepository extends JpaRepository<PointTask, Long> {
    @Query("""
        SELECT t FROM PointTask t 
        JOIN FETCH t.transaction 
        WHERE t.id = :taskId 
        AND t.status IN :statuses 
        AND t.retryCount < :maxRetry
    """)
    Optional<PointTask> findProcessableTask(
            @Param("taskId") Long taskId,
            @Param("statuses") List<TaskStatus> statuses,
            @Param("maxRetry") int maxRetry
    );
}