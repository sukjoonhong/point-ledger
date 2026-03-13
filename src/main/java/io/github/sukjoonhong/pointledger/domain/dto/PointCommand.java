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
        validateContext(type, orderId);
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new PointLedgerException(PointErrorCode.INVALID_AMOUNT,
                    "Requested amount: " + amount);
        }
    }

    private void validateContext(PointTransactionType type, String orderId) {
        final boolean isOrderType = (type == PointTransactionType.USE || type == PointTransactionType.CANCEL_USE);

        if (isOrderType && !StringUtils.hasText(orderId)) {
            throw new PointLedgerException(PointErrorCode.ORDER_ID_REQUIRED,
                    "Transaction type: " + type);
        }

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