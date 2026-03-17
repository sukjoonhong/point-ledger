package io.github.sukjoonhong.pointledger.domain.dto;

import lombok.Builder;

@Builder
public record PointResponse(
        String pointKey,
        Long memberId,
        Long processedAmount,
        Long balance,
        String type,
        String message
) {}
