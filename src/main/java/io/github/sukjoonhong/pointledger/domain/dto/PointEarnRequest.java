package io.github.sukjoonhong.pointledger.domain.dto;

import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record PointEarnRequest(
        @NotNull Long memberId,
        @NotNull @Min(1) Long amount,
        @NotBlank String pointKey,
        PointSource source,       // null이면 ORDER로 기본 할당
        String description
) {}
