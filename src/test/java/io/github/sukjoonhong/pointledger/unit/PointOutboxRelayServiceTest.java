package io.github.sukjoonhong.pointledger.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointOutbox;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.repository.PointOutboxRepository;
import io.github.sukjoonhong.pointledger.service.PointOutboxRelayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private PointOutboxRelayService relayService;

    @Test
    @DisplayName("정상 흐름: 재시도 가능한 이벤트를 발행한 후 PROCESSED로 변경해야 한다")
    void relay_Success() throws Exception {
        // given
        PointOutbox outbox = createOutbox(1L, PointOutbox.OutboxStatus.PENDING, 0);

        given(outboxRepository.findRetryableEvents(anyCollection(), anyInt(), any(Pageable.class)))
                .willReturn(List.of(outbox));
        given(objectMapper.readValue(anyString(), eq(PointCommand.class)))
                .willReturn(mock(PointCommand.class));

        // when
        relayService.relay();

        // then
        assertThat(outbox.getStatus()).isEqualTo(PointOutbox.OutboxStatus.PROCESSED);
        verify(messagePublisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("엣지 케이스: 재시도 횟수가 남은 상태에서 실패 시, 횟수만 증가하고 알람은 보내지 않는다")
    void relay_Retry_WithinLimit() throws Exception {
        // given
        PointOutbox outbox = createOutbox(1L, PointOutbox.OutboxStatus.PENDING, 0);
        given(outboxRepository.findRetryableEvents(anyCollection(), anyInt(), any(Pageable.class)))
                .willReturn(List.of(outbox));
        given(objectMapper.readValue(anyString(), eq(PointCommand.class)))
                .willReturn(mock(PointCommand.class));

        // 발행 실패 시뮬레이션
        doThrow(new RuntimeException("Temporary Network Issue")).when(messagePublisher).publish(any());

        // when
        relayService.relay();

        // then
        assertThat(outbox.getStatus()).isEqualTo(PointOutbox.OutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
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