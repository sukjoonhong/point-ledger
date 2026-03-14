package io.github.sukjoonhong.pointledger.domain.type;

public enum PointSequenceStatus {
    EXPECTED,           // 정상 (current + 1)
    ALREADY_PROCESSED,  // 중복/과거 (<= current)
    GAP_DETECTED        // 갭 발생 (> current + 1)
}