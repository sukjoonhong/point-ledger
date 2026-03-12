package io.github.sukjoonhong.pointledger.domain.type;

public enum PointSource {
    ORDER,   // 주문/결제 적립
    ADMIN,   // 관리자 수동 지급
    EVENT,   // 이벤트 참여
    SYSTEM   // 시스템 보상 (장애 보상 등)
}