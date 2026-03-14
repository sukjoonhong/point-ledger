package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.domain.type.PointSequenceStatus;
import org.springframework.stereotype.Component;

@Component
public class PointSequenceValidator {

    /**
     * 현재 시퀀스와 유입된 시퀀스를 비교하여 상태를 반환합니다.
     */
    public PointSequenceStatus validate(long currentSeq, long incomingSeq) {
        if (incomingSeq <= currentSeq) {
            return PointSequenceStatus.ALREADY_PROCESSED;
        }

        if (incomingSeq == currentSeq + 1) {
            return PointSequenceStatus.EXPECTED;
        }

        return PointSequenceStatus.GAP_DETECTED;
    }
}