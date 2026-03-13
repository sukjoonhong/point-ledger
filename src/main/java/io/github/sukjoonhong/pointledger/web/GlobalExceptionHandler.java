package io.github.sukjoonhong.pointledger.web;

import io.github.sukjoonhong.pointledger.domain.dto.ErrorResponse;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PointLedgerException.class)
    protected ResponseEntity<ErrorResponse> handlePointLedgerException(PointLedgerException e) {
        PointErrorCode errorCode = e.getErrorCode();

        logger.error("[BUSINESS_EXCEPTION] Code: {}, Status: {}, Message: {}",
                errorCode.getCode(), errorCode.getStatus(), e.getMessage());

        ErrorResponse response = ErrorResponse.of(errorCode, e.getMessage());

        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        logger.error("[UNEXPECTED_SYSTEM_EXCEPTION] Message: {}", e.getMessage(), e);

        ErrorResponse response = ErrorResponse.of(
                PointErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred."
        );

        return new ResponseEntity<>(response, PointErrorCode.INTERNAL_SERVER_ERROR.getStatus());
    }
}