package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.repository.PointTaskRepository;
import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointLedgerServiceTest {

    @Mock
    private PointTransactionRepository transactionRepository;

    @Mock
    private PointTaskRepository taskRepository;

    @InjectMocks
    private PointLedgerService pointLedgerService;

    @Test
    @DisplayName("정상적인 이벤트 수신 시 원장과 태스크가 모두 저장되어야 한다")
    void saveLedgerAndTaskSuccessfully() {
        // given
        PointCommand command = PointCommand.builder()
                .pointKey("evt-123")
                .memberId(1L)
                .amount(1000L)
                .build();

        PointTransaction mockTransaction = command.toEntity();

        given(transactionRepository.existsByPointKey(anyString())).willReturn(false);
        given(transactionRepository.save(any(PointTransaction.class))).willReturn(mockTransaction);

        // when
        pointLedgerService.onMessage(command);

        // then
        verify(transactionRepository, times(1)).save(any(PointTransaction.class));
        verify(taskRepository, times(1)).save(any(PointTask.class));
    }

    @Test
    @DisplayName("이미 존재하는 pointKey가 들어오면 중복으로 판단하고 저장 로직을 타지 않아야 한다")
    void skipDuplicatePointKey() {
        // given
        PointCommand command = PointCommand.builder()
                .pointKey("evt-duplicate")
                .build();

        given(transactionRepository.existsByPointKey("evt-duplicate")).willReturn(true);

        // when
        pointLedgerService.onMessage(command);

        // then
        verify(transactionRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
    }
}