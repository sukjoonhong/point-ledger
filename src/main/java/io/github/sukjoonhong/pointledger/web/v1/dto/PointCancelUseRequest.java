package io.github.sukjoonhong.pointledger.web.v1.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record PointCancelUseRequest(
        @NotNull Long memberId,
        @NotNull @Min(1) Long amount,
        @NotBlank String pointKey,
        @NotBlank String orderId
) {}
