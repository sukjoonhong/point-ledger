package io.github.sukjoonhong.pointledger.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import io.github.sukjoonhong.pointledger.application.service.event.PointOutboxCapturedEvent;
import io.github.sukjoonhong.pointledger.application.worker.subsciber.PointOutboxEventSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointOutboxEventSubscriberTest {

    @Mock
    private PointOutboxRepository outboxRepository;

    @Mock
    private PointMessagePublisher messagePublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PointOutboxEventSubscriber relayService;

    @Test
    @DisplayName("정상 흐름: 단건 이벤트를 발행한 후 PROCESSED 상태로 완료되어야 한다")
    void onCaptured_Success() throws Exception {
        // given
        Long outboxId = 1L;
        PointOutbox outbox = createOutbox(outboxId, PointOutbox.OutboxStatus.PENDING, 0);

        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(outbox));
        given(objectMapper.readValue(anyString(), eq(PointCommand.class)))
                .willReturn(mock(PointCommand.class));

        // when
        relayService.propagate(new PointOutboxCapturedEvent(outboxId));

        // then
        assertThat(outbox.getStatus()).isEqualTo(PointOutbox.OutboxStatus.PROCESSED);
        verify(messagePublisher, times(1)).publish(any());
        verify(outboxRepository, times(1)).save(outbox);
        // 성공 시에는 재발행 이벤트가 호출되지 않아야 함
        verify(eventPublisher, never()).publishEvent(any(PointOutboxCapturedEvent.class));
    }

    @Test
    @DisplayName("재시도 흐름: 발행 실패 시 FAILED 상태가 되고 재시도 이벤트가 발행되어야 한다")
    void onCaptured_Failure_ShouldTriggerRetryEvent() throws Exception {
        // given
        Long outboxId = 1L;
        PointOutbox outbox = createOutbox(outboxId, PointOutbox.OutboxStatus.PENDING, 0);

        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(outbox));
        given(objectMapper.readValue(anyString(), eq(PointCommand.class)))
                .willReturn(mock(PointCommand.class));

        // 발행 실패 시뮬레이션
        doThrow(new RuntimeException("Broker is down")).when(messagePublisher).publish(any());

        // when
        relayService.propagate(new PointOutboxCapturedEvent(outboxId));

        // then
        assertThat(outbox.getStatus()).isEqualTo(PointOutbox.OutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);

        // 재시도 이벤트를 다시 던졌는지 검증
        verify(eventPublisher, times(1)).publishEvent(any(PointOutboxCapturedEvent.class));
        verify(outboxRepository, times(1)).save(outbox);
    }

    @Test
    @DisplayName("임계치 도달: 최대 재시도 횟수 초과 시 더 이상 이벤트를 재발행하지 않는다")
    void onCaptured_MaxRetry_Reached() throws Exception {
        // given
        Long outboxId = 1L;
        // 이미 4번 실패해서 이번이 마지막 5번째 시도인 상황
        PointOutbox outbox = createOutbox(outboxId, PointOutbox.OutboxStatus.FAILED, 4);

        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(outbox));
        given(objectMapper.readValue(anyString(), eq(PointCommand.class)))
                .willReturn(mock(PointCommand.class));

        doThrow(new RuntimeException("Persistent Error")).when(messagePublisher).publish(any());

        // when
        relayService.propagate(new PointOutboxCapturedEvent(outboxId));

        // then
        assertThat(outbox.getRetryCount()).isEqualTo(5);
        verify(eventPublisher, never()).publishEvent(any(PointOutboxCapturedEvent.class));
    }

    private PointOutbox createOutbox(Long id, PointOutbox.OutboxStatus status, int retryCount) {
        return PointOutbox.builder()
                .id(id)
                .payload("{\"pointKey\":\"evt-1\"}")
                .status(status)
                .retryCount(retryCount)
                .build();
    }
}