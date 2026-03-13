package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointUsageDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {
    List<PointUsageDetail> findAllByOrderIdOrderByIdDesc(String orderId);
}
