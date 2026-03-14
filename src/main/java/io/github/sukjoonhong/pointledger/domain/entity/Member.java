package io.github.sukjoonhong.pointledger.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 외부 회원 서비스의 데이터를 미러링하는 엔티티
 * 실제 데이터 권한은 회원 서비스에 있으며, 포인트 시스템에서는 식별자 참조용으로만 사용함
 */
@Entity
@Getter
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String loginId;
}