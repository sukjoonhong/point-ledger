package io.github.sukjoonhong.pointledger.domain.type;

public enum PointTransactionType {
    EARN,        // 적립
    USE,         // 사용
    CANCEL_EARN, // 적립 취소
    CANCEL_USE,  // 사용 취소 (환불)
    RE_EARN      // 신규 발행
}