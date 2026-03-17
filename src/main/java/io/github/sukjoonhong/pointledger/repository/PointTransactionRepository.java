package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    boolean existsByPointKey(String pointKey);

    @Modifying
    @Transactional
    @Query("DELETE FROM PointAsset a WHERE a.memberId = :memberId")
    void deleteAllByMemberId(Long memberId);

    List<PointTransaction> findAllByMemberId(Long memberId);

    @Query("""
                SELECT pt FROM PointTransaction pt 
                WHERE pt.memberId = :memberId 
                  AND pt.sequenceNum > :seqNum 
                ORDER BY pt.sequenceNum ASC
            """)
    List<PointTransaction> findTransactionsAfterSeq(
            @Param("memberId") Long memberId,
            @Param("seqNum") Long seqNum
    );

    Page<PointTransaction> findAllByMemberId(Long memberId, Pageable pageable);
}
