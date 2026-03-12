package io.github.sukjoonhong.pointledger.infrastructure.messaging;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;

public interface PointDlqPublisher {
    void sendToDlq(PointCommand command, String reason);
}