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
}
