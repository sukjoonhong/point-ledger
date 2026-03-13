package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PointAssetRepository extends JpaRepository<PointAsset, Long> {
    @Query("""
        SELECT asset FROM PointAsset asset 
        WHERE asset.memberId = :memberId 
          AND asset.status = 'ACTIVE' 
          AND asset.remainingAmount > 0 
        ORDER BY 
            CASE WHEN asset.source = 'ADMIN' THEN 1 ELSE 2 END ASC, 
            asset.expirationDate ASC, 
            asset.id ASC
    """)
    List<PointAsset> findAllForDeduction(@Param("memberId") Long memberId);

    Optional<PointAsset> findByPointKey(String pointKey);
}
