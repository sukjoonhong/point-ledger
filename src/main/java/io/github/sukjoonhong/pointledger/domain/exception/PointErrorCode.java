package io.github.sukjoonhong.pointledger.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode {
    // 1. Wallet (W): 지갑 상태 및 잔액 관련
    INSUFFICIENT_BALANCE("W001", "Insufficient point balance.", HttpStatus.UNPROCESSABLE_ENTITY),
    EXCEED_MAX_HOLDING_LIMIT("W002", "Total balance exceeds maximum holding limit.", HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_SEQUENCE("W003", "Invalid sequence: Event already processed.", HttpStatus.CONFLICT),
    SEQUENCE_GAP_DETECTED("W004", "Sequence gap detected during replay.", HttpStatus.CONFLICT),
    WALLET_UNDER_RECOVERY("W005", "Wallet recovery in progress. Please try again later.", HttpStatus.UNPROCESSABLE_ENTITY),

    // 2. Asset (A): 개별 자산 관련
    INVALID_EARN_AMOUNT("A001", "Earn amount is out of policy range.", HttpStatus.BAD_REQUEST),
    ASSET_NOT_ACTIVE("A002", "Operation failed. Asset status is not ACTIVE.", HttpStatus.UNPROCESSABLE_ENTITY),
    ASSET_ALREADY_USED("A003", "Cannot cancel earning: Asset has already been used.", HttpStatus.CONFLICT),
    INSUFFICIENT_ASSET_BALANCE("A004", "Insufficient individual asset balance.", HttpStatus.UNPROCESSABLE_ENTITY),
    ASSET_NOT_FOUND("A005", "Original earning asset not found.", HttpStatus.NOT_FOUND),
    INVALID_EXPIRATION_RANGE("A006", "Expiration date must be between 1 day and 5 years.", HttpStatus.BAD_REQUEST),
    ORIGINAL_KEY_REQUIRED("A007", "Original point key is required for cancellation or compensation transactions.", HttpStatus.BAD_REQUEST),

    // 3. Usage & Refund (U)
    INVALID_REFUND_AMOUNT("U001", "Refund amount exceeds original usage amount.", HttpStatus.BAD_REQUEST),
    ALREADY_FULLY_REFUNDED("U002", "Already fully refunded. Cannot refund again.", HttpStatus.CONFLICT),

    // 4. Command/Request (C): 입력값 검증
    INVALID_AMOUNT("C001", "Amount must be strictly positive.", HttpStatus.BAD_REQUEST),
    ORDER_ID_REQUIRED("C002", "OrderId is required.", HttpStatus.BAD_REQUEST),
    ORDER_ID_NOT_ALLOWED("C003", "OrderId must be null for this type.", HttpStatus.BAD_REQUEST),
    CONCURRENT_REQUEST("C004", "Another request with the same key is being processed or already completed.", HttpStatus.CONFLICT),

    // 5. System (S): 인프라 및 알 수 없는 오류
    UNSUPPORTED_TX_TYPE("S001", "Unsupported transaction type.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("S002", "An unexpected system error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}