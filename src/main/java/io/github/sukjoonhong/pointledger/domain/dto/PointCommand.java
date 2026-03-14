package io.github.sukjoonhong.pointledger.domain.dto;

import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import lombok.Builder;
import org.springframework.util.StringUtils;

/**
 * @param orderId Required for USE or CANCEL_USE
 */
public record PointCommand(
        Long memberId,
        Long amount,
        String pointKey,
        String originalPointKey, // Reference to the original transaction
        Long sequenceNum,
        PointTransactionType type,
        PointSource source,
        String description,
        String orderId
) {
    @Builder
    public PointCommand {
        validateAmount(amount);
        validateContext(type, orderId, originalPointKey);
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new PointLedgerException(PointErrorCode.INVALID_AMOUNT,
                    "Requested amount: " + amount);
        }
    }

    private void validateContext(PointTransactionType type, String orderId, String originalPointKey) {
        // 1. 주문 번호 필수 체크 (사용/사용취소)
        final boolean isOrderType = (type == PointTransactionType.USE || type == PointTransactionType.CANCEL_USE);
        if (isOrderType && !StringUtils.hasText(orderId)) {
            throw new PointLedgerException(PointErrorCode.ORDER_ID_REQUIRED,
                    "Transaction type: " + type + " requires orderId");
        }

        // 2. 원본 키 필수 체크 (적립취소/사용취소)
        final boolean isCancelType = (type == PointTransactionType.CANCEL_EARN || type == PointTransactionType.CANCEL_USE);
        if (isCancelType && !StringUtils.hasText(originalPointKey)) {
            throw new PointLedgerException(PointErrorCode.ORIGINAL_KEY_REQUIRED,
                    "Transaction type: " + type + " requires originalPointKey");
        }

        // 3. 비즈니스 맥락에 맞지 않는 데이터 방어
        if (!isOrderType && StringUtils.hasText(orderId)) {
            throw new PointLedgerException(PointErrorCode.ORDER_ID_NOT_ALLOWED,
                    "Transaction type: " + type + ", OrderId: " + orderId);
        }
    }

    public PointTransaction toEntity() {
        return PointTransaction.builder()
                .memberId(this.memberId)
                .amount(this.amount)
                .pointKey(this.pointKey)
                .originalPointKey(this.originalPointKey)
                .sequenceNum(this.sequenceNum)
                .type(this.type)
                .source(this.source)
                .description(this.description)
                .orderId(this.orderId)
                .build();
    }
}