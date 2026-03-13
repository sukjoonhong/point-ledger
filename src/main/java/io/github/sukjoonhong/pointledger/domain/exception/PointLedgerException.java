package io.github.sukjoonhong.pointledger.domain.exception;

import lombok.Getter;

@Getter
public class PointLedgerException extends RuntimeException {
    private final PointErrorCode errorCode;

    public PointLedgerException(PointErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PointLedgerException(PointErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}