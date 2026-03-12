package io.github.sukjoonhong.pointledger.domain.dto;

import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PointCommand {
    private final Long memberId;
    private final Long amount;
    private final String pointKey;    // Idempotency Key
    private final Long sequenceNum;   // Sequence Identifier
    private final PointTransactionType type;
    private final PointSource source;
    private final String description;

    public boolean isEarn() {
        return type == PointTransactionType.EARN;
    }

    public PointTransaction toEntity() {
        return PointTransaction.builder()
                .memberId(this.memberId)
                .amount(this.amount)
                .pointKey(this.pointKey)
                .sequenceNum(this.sequenceNum)
                .type(this.type)
                .source(this.source)
                .description(this.description)
                .build();
    }
}