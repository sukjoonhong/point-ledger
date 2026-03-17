package io.github.sukjoonhong.pointledger.unit;

import io.github.sukjoonhong.pointledger.application.service.event.PointEventPublisher;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.infrastructure.lock.DistributedLockManager;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import io.github.sukjoonhong.pointledger.application.service.ingress.PointEventIngestor;
import io.github.sukjoonhong.pointledger.application.service.event.PointTaskCapturedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointEventIngestorTest {

    @Mock private PointTransactionRepository transactionRepository;
    @Mock private PointTaskRepository taskRepository;
    @Mock private DistributedLockManager lockManager;
    @Mock private PointEventPublisher eventPublisher;

    @InjectMocks
    private PointEventIngestor pointEventIngestor;

    @BeforeEach
    void setUp() {
        lenient().when(lockManager.executeWithLock(anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(1);
                    return action.get();
                });
    }

    @Test
    @DisplayName("정상적인 이벤트 수신 시 원장과 태스크가 저장되고, 캡처 이벤트가 발행되어야 한다")
    void saveLedgerAndTaskSuccessfully() {
        // given
        PointCommand command = PointCommand.builder()
                .pointKey("evt-123")
                .memberId(1L)
                .amount(1000L)
                .build();

        PointTransaction mockTransaction = command.toEntity();

        PointTask mockTask = PointTask.builder()
                .id(999L)
                .transaction(mockTransaction)
                .build();

        given(transactionRepository.existsByPointKey(anyString())).willReturn(false);
        given(transactionRepository.save(any(PointTransaction.class))).willReturn(mockTransaction);
        given(taskRepository.save(any(PointTask.class))).willReturn(mockTask);

        // when
        pointEventIngestor.onMessage(command);

        // then
        verify(transactionRepository, times(1)).save(any(PointTransaction.class));
        verify(taskRepository, times(1)).save(any(PointTask.class));

        // [이벤트 검증] 캡처 이벤트가 실제로 발행되었는지 확인
        verify(eventPublisher, times(1)).publish(any(PointTaskCapturedEvent.class));
    }

    @Test
    @DisplayName("이미 존재하는 pointKey가 들어오면 저장 로직과 이벤트 발행을 건너뛴다")
    void skipDuplicatePointKey() {
        // given
        PointCommand command = PointCommand.builder()
                .pointKey("evt-duplicate")
                .amount(10L)
                .build();

        given(transactionRepository.existsByPointKey("evt-duplicate")).willReturn(true);

        // when
        pointEventIngestor.onMessage(command);

        // then
        verify(transactionRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}