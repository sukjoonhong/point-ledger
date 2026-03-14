package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointUsageDetail;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {
    @Query("SELECT d FROM PointUsageDetail d WHERE d.orderId = :orderId")
    List<PointUsageDetail> findAllForRefund(@Param("orderId") String orderId, Sort sort);

    @Query("SELECT d FROM PointUsageDetail d WHERE d.pointAssetId = :assetId")
    List<PointUsageDetail> findAllByPointAssetId(@Param("assetId") Long assetId);

    @Query("SELECT d FROM PointUsageDetail d WHERE d.orderId = :orderId")
    List<PointUsageDetail> findAllByOrderId(@Param("orderId") String orderId);
}
