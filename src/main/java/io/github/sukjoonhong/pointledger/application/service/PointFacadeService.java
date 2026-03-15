package io.github.sukjoonhong.pointledger.application.service;

import io.github.sukjoonhong.pointledger.application.api.v1.dto.*;
import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.domain.exception.PointErrorCode;
import io.github.sukjoonhong.pointledger.domain.exception.PointLedgerException;
import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import io.github.sukjoonhong.pointledger.domain.type.PointTransactionType;
import io.github.sukjoonhong.pointledger.repository.PointAssetRepository;
import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기 API를 위한 Facade 서비스.
 * 기존 비동기 파이프라인(enqueue → queue → process)의 핵심 컴포넌트를
 * 단일 트랜잭션 내에서 직접 호출하여 즉시 결과를 반환합니다.
 */
@Service
@RequiredArgsConstructor
public class PointFacadeService {

    private final Logger logger = LoggerFactory.getLogger(PointFacadeService.class);

    private final PointWalletRepository walletRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointAssetRepository assetRepository;
    private final PointBusinessRouter businessRouter;
    private final PointPolicyManager policyManager;

    // ──────────────────────────────────────────────
    // 적립
    // ──────────────────────────────────────────────
    @Transactional
    public PointResponse earn(PointEarnRequest request) {
        PointWallet wallet = getOrCreateWalletWithLock(request.memberId());
        long nextSeq = wallet.getLastSequenceNum() + 1;

        PointTransaction tx = saveTransaction(
                request.memberId(),
                request.amount(),
                request.pointKey(),
                null,
                nextSeq,
                PointTransactionType.EARN,
                request.source() != null ? request.source() : PointSource.ORDER,
                request.description(),
                null
        );

        businessRouter.route(wallet, tx);
        wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
        walletRepository.save(wallet);

        logger.info("[SYNC_EARN] PointKey: {}, Amount: {}, Balance: {}",
                tx.getPointKey(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "포인트가 적립되었습니다.");
    }

    // ──────────────────────────────────────────────
    // 적립 취소
    // ──────────────────────────────────────────────
    @Transactional
    public PointResponse cancelEarn(PointCancelEarnRequest request) {
        PointWallet wallet = getOrCreateWalletWithLock(request.memberId());
        long nextSeq = wallet.getLastSequenceNum() + 1;

        // 원본 적립 자산 조회하여 금액 확인
        PointAsset originalAsset = assetRepository.findByPointKey(request.originalPointKey())
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "원본 적립 내역을 찾을 수 없습니다. PointKey: " + request.originalPointKey()));

        PointTransaction tx = saveTransaction(
                request.memberId(),
                originalAsset.getAmount(),
                request.pointKey(),
                request.originalPointKey(),
                nextSeq,
                PointTransactionType.CANCEL_EARN,
                originalAsset.getSource(),
                "적립 취소: " + request.originalPointKey(),
                null
        );

        businessRouter.route(wallet, tx);
        wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
        walletRepository.save(wallet);

        logger.info("[SYNC_CANCEL_EARN] PointKey: {}, OriginalKey: {}, CancelledAmount: {}, Balance: {}",
                tx.getPointKey(), request.originalPointKey(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "적립이 취소되었습니다.");
    }

    // ──────────────────────────────────────────────
    // 사용
    // ──────────────────────────────────────────────
    @Transactional
    public PointResponse use(PointUseRequest request) {
        PointWallet wallet = getOrCreateWalletWithLock(request.memberId());
        long nextSeq = wallet.getLastSequenceNum() + 1;

        PointTransaction tx = saveTransaction(
                request.memberId(),
                request.amount(),
                request.pointKey(),
                null,
                nextSeq,
                PointTransactionType.USE,
                PointSource.ORDER,
                null,
                request.orderId()
        );

        businessRouter.route(wallet, tx);
        wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
        walletRepository.save(wallet);

        logger.info("[SYNC_USE] PointKey: {}, OrderId: {}, Amount: {}, Balance: {}",
                tx.getPointKey(), request.orderId(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "포인트가 사용되었습니다.");
    }

    // ──────────────────────────────────────────────
    // 사용 취소
    // ──────────────────────────────────────────────
    @Transactional
    public PointResponse cancelUse(PointCancelUseRequest request) {
        PointWallet wallet = getOrCreateWalletWithLock(request.memberId());
        long nextSeq = wallet.getLastSequenceNum() + 1;

        PointTransaction tx = saveTransaction(
                request.memberId(),
                request.amount(),
                request.pointKey(),
                request.pointKey(),     // 사용취소: 자기 자신이 원본 참조
                nextSeq,
                PointTransactionType.CANCEL_USE,
                PointSource.ORDER,
                null,
                request.orderId()
        );

        businessRouter.route(wallet, tx);
        wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
        walletRepository.save(wallet);

        logger.info("[SYNC_CANCEL_USE] PointKey: {}, OrderId: {}, RefundAmount: {}, Balance: {}",
                tx.getPointKey(), request.orderId(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "사용이 취소되었습니다.");
    }

    // ──────────────────────────────────────────────
    // 잔액 조회
    // ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PointResponse getBalance(Long memberId) {
        PointWallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "지갑이 존재하지 않습니다. MemberId: " + memberId));

        return PointResponse.builder()
                .memberId(wallet.getMemberId())
                .balance(wallet.getBalance())
                .type("BALANCE")
                .message("현재 잔액을 조회했습니다.")
                .build();
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private PointWallet getOrCreateWalletWithLock(Long memberId) {
        try {
            return walletRepository.findByMemberIdWithLock(memberId)
                    .orElseGet(() -> {
                        PointWallet newWallet = PointWallet.builder()
                                .memberId(memberId)
                                .balance(0L)
                                .lastSequenceNum(0L)
                                .build();
                        return walletRepository.save(newWallet);
                    });
        } catch (DataIntegrityViolationException e) {
            return walletRepository.findByMemberIdWithLock(memberId)
                    .orElseThrow(() -> new PointLedgerException(PointErrorCode.INTERNAL_SERVER_ERROR,
                            "지갑 생성 충돌 복구 실패. MemberId: " + memberId));
        }
    }

    private PointTransaction saveTransaction(
            Long memberId, Long amount, String pointKey, String originalPointKey,
            Long sequenceNum, PointTransactionType type, PointSource source,
            String description, String orderId
    ) {
        return transactionRepository.save(PointTransaction.builder()
                .memberId(memberId)
                .amount(amount)
                .pointKey(pointKey)
                .originalPointKey(originalPointKey)
                .sequenceNum(sequenceNum)
                .type(type)
                .source(source)
                .description(description)
                .orderId(orderId)
                .build());
    }

    private PointResponse toResponse(PointTransaction tx, PointWallet wallet, String message) {
        return PointResponse.builder()
                .pointKey(tx.getPointKey())
                .memberId(wallet.getMemberId())
                .processedAmount(tx.getAmount())
                .balance(wallet.getBalance())
                .type(tx.getType().name())
                .message(message)
                .build();
    }
}
