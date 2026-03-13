package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointTaskRepository extends JpaRepository<PointTask, Long> {
    @Query("""
            SELECT t FROM PointTask t
                        WHERE t.status IN :statuses
                        AND t.retryCount < :limit
                        ORDER BY t.createdAt ASC
            """)
    List<PointTask> findTasksToProcess(
            @Param("statuses") List<TaskStatus> statuses,
            @Param("limit") int limit,
            Pageable pageable
    );
}