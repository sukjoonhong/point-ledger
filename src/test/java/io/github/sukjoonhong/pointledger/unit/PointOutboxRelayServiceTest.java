package io.github.sukjoonhong.pointledger.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.application.worker.PointOutboxRelayService;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointOutboxRelayServiceTest {

    @Mock
    private PointOutboxRepository outboxRepository;

    @Mock
    private PointMessagePublisher messagePublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PointOutboxRelayService outboxRelayService;

    private static final int MAX_RETRY_COUNT = 5;

    @Test
    @DisplayName("Success: Outbox relay should publish message and call complete()")
    void relay_Success() throws Exception {
        // given
        Long outboxId = 100L;
        PointOutbox outbox = spy(createOutbox(outboxId, PointOutbox.OutboxStatus.PENDING, 0));
        PointCommand mockCommand = PointCommand.builder().memberId(1L).amount(100L).build();

        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(outbox));
        given(objectMapper.readValue(anyString(), eq(PointCommand.class))).willReturn(mockCommand);

        // when
        outboxRelayService.relay(outboxId);

        // then
        verify(messagePublisher, times(1)).publish(any(PointCommand.class));
        verify(outbox, times(1)).complete(); // 엔티티의 complete() 호출 검증!
    }

    @Test
    @DisplayName("Retry: On broker failure, outbox should call fail() to update status")
    void relay_Failure_ShouldCallFail() throws Exception {
        // given
        Long outboxId = 200L;
        PointOutbox outbox = spy(createOutbox(outboxId, PointOutbox.OutboxStatus.PENDING, 0));

        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(outbox));
        given(objectMapper.readValue(anyString(), eq(PointCommand.class))).willReturn(mock(PointCommand.class));

        // 예외 발생 시뮬레이션
        doThrow(new RuntimeException("Message broker connection refused")).when(messagePublisher).publish(any());

        // when
        outboxRelayService.relay(outboxId);

        // then
        verify(outbox, times(1)).fail();
    }

    @Test
    @DisplayName("Termination: No relay processing should occur if max retries reached")
    void relay_MaxRetry_Reached() throws Exception {
        // given
        Long outboxId = 300L;
        // 이미 5번 꽉 채운 아웃박스
        PointOutbox outbox = spy(createOutbox(outboxId, PointOutbox.OutboxStatus.FAILED, MAX_RETRY_COUNT));

        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(outbox));

        // when
        outboxRelayService.relay(outboxId);

        // then
        // relay 내부의 filter 조건에 걸려서 일찍 리턴되므로 아무 일도 일어나지 않아야 함
        verify(messagePublisher, never()).publish(any());
        verify(outbox, never()).fail();
        verify(outbox, never()).complete();
    }

    private PointOutbox createOutbox(Long id, PointOutbox.OutboxStatus status, int retryCount) {
        return PointOutbox.builder()
                .id(id)
                .payload("{\"pointKey\":\"admin-test-key\"}")
                .status(status)
                .retryCount(retryCount)
                .build();
    }
}