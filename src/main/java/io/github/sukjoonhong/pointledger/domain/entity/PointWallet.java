package io.github.sukjoonhong.pointledger.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "point_wallet",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk__point_wallet__member_id", columnNames = {"member_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "total_balance", nullable = false)
    private Long totalBalance;

    private Long lastSequenceNum;

    @Version
    private Long version;

    @Builder
    public PointWallet(Long memberId, Long totalBalance) {
        this.memberId = memberId;
        this.totalBalance = (totalBalance != null) ? totalBalance : 0L;
    }

    public void updateSequence(Long newSequenceNum) {
        this.lastSequenceNum = newSequenceNum;
    }
}