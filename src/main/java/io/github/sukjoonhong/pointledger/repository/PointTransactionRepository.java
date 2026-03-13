package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    boolean existsByPointKey(String pointKey);

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
}
