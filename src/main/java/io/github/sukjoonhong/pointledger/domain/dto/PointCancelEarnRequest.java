package io.github.sukjoonhong.pointledger.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record PointCancelEarnRequest(
        @NotNull Long memberId,
        @NotBlank String pointKey,          // 이 취소 요청의 고유 키
        @NotBlank String originalPointKey   // 원본 적립의 pointKey
) {}
