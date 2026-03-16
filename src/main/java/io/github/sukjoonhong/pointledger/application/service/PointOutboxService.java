package io.github.sukjoonhong.pointledger.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.application.service.event.PointEventPublisher;
import io.github.sukjoonhong.pointledger.application.service.event.PointOutboxCapturedEvent;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointOutboxService {
    private final PointOutboxRepository outboxRepository;
    private final PointEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public void createReEarnOutbox(PointWallet wallet, PointTransaction tx, long amount) {
        String newKey = "RE-" + UUID.randomUUID().toString().substring(0, 8);
        PointCommand command = PointCommand.builder()
                .memberId(wallet.getMemberId())
                .amount(amount)
                .pointKey(newKey)
                .type(PointTransactionType.RE_EARN)
                .source(PointSource.SYSTEM)
                .description("Compensation for expired asset. Original OrderID: " + tx.getOrderId())
                .build();

        try {
            PointOutbox outbox = PointOutbox.builder()
                    .eventType("RE_EARN")
                    .payload(objectMapper.writeValueAsString(command))
                    .status(PointOutbox.OutboxStatus.PENDING)
                    .build();

            outboxRepository.save(outbox);
            eventPublisher.publish(new PointOutboxCapturedEvent(outbox.getId()));
        } catch (JsonProcessingException e) {
            throw new PointLedgerException(PointErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize RE_EARN");
        }
    }
}