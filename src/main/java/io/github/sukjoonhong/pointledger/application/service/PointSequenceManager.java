package io.github.sukjoonhong.pointledger.application.service;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PointSequenceManager {

    private final Logger logger = LoggerFactory.getLogger(PointSequenceManager.class);
    private final PointWalletRepository walletRepository;

    /**
     * 시퀀스 번호가 누락된 경우에만 자동으로 다음 시퀀스를 할당합니다.
     * Record의 불변성을 위해 새로운 객체를 생성하여 반환합니다.
     */
    @Transactional(readOnly = true)
    public PointCommand fillSequenceIfEmpty(PointCommand command) {
        if (command.sequenceNum() != null) {
            return command;
        }

        Long nextSeq = walletRepository.findByMemberId(command.memberId())
                .map(PointWallet::getLastSequenceNum)
                .map(last -> last + 1)
                .orElse(1L);

        logger.info("[AUTO_SEQUENCE_GENERATED] MemberID: {}, GeneratedSeq: {}",
                command.memberId(), nextSeq);

        return PointCommand.builder()
                .memberId(command.memberId())
                .amount(command.amount())
                .pointKey(command.pointKey())
                .originalPointKey(command.originalPointKey())
                .sequenceNum(nextSeq)
                .type(command.type())
                .source(command.source())
                .description(command.description())
                .orderId(command.orderId())
                .build();
    }
}