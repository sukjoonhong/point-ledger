package io.github.sukjoonhong.pointledger.repository;

import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
    /**
     * 비관적 락을 사용하여 지갑 정보를 조회.
     * lastSequenceNum과 잔액 정합성을 보장하기 위해 SELECT ... FOR UPDATE 실행.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM PointWallet w WHERE w.memberId = :memberId")
    Optional<PointWallet> findByMemberIdWithLock(@Param("memberId") Long memberId);

    Optional<PointWallet> findByMemberId(@Param("memberId") Long memberId);
}