package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.type.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PointTaskRepository extends JpaRepository<PointTask, Long> {
    List<PointTask> findTop20ByStatusOrderByCreatedAtAsc(TaskStatus status);
}