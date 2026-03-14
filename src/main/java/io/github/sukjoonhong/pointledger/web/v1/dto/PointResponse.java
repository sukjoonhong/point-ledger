package io.github.sukjoonhong.pointledger.web.v1.dto;

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
