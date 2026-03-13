package io.github.sukjoonhong.pointledger.domain.dto;

import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 전역 예외 처리를 위한 공통 응답 포맷
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorResponse {

    private String code;
    private String message;
    private String detail;
    private OffsetDateTime timestamp;

    private ErrorResponse(PointErrorCode errorCode, String detail) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.detail = detail;
        this.timestamp = OffsetDateTime.now();
    }

    public static ErrorResponse of(PointErrorCode errorCode, String detail) {
        return new ErrorResponse(errorCode, detail);
    }
}