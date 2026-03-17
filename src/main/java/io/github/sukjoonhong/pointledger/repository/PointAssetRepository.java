package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.type.PointAssetStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PointAssetRepository extends JpaRepository<PointAsset, Long> {
    @Query("""
        SELECT a FROM PointAsset a 
        WHERE a.memberId = :memberId 
          AND a.status = 'ACTIVE' 
          AND a.remainingAmount > 0
    """)
    List<PointAsset> findAllForDeduction(@Param("memberId") Long memberId, Sort sort);

    List<PointAsset> findAllByMemberIdAndRemainingAmountGreaterThan(Long memberId, long amount);

    Optional<PointAsset> findByPointKey(String pointKey);

    @Modifying
    @Transactional
    @Query("DELETE FROM PointAsset a WHERE a.memberId = :memberId")
    void deleteAllByMemberId(Long memberId);

    List<PointAsset> findAllByStatusAndExpirationDateBefore(PointAssetStatus status, OffsetDateTime dateTime);
}
