package io.github.sukjoonhong.pointledger.domain.type;

public enum OrderStatus {
    PENDING,    // 결제/차감 대기
    COMPLETED,  // 완료
    CANCELLED,  // 취소
    FAILED      // 실패 (Saga 롤백 대상)
}
