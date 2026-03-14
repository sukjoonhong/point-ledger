package io.github.sukjoonhong.pointledger.web;

import io.github.sukjoonhong.pointledger.domain.dto.ErrorResponse;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("입력값 검증 실패");

        logger.warn("[VALIDATION_ERROR] {}", detail);

        return new ResponseEntity<>(
                ErrorResponse.of(PointErrorCode.INVALID_AMOUNT, detail),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        logger.warn("[DB_CONSTRAINT] {}", e.getMostSpecificCause().getMessage());

        return new ResponseEntity<>(
                ErrorResponse.of(PointErrorCode.CONCURRENT_REQUEST, "중복된 요청이거나 데이터 제약 조건 위반입니다."),
                HttpStatus.CONFLICT
        );
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