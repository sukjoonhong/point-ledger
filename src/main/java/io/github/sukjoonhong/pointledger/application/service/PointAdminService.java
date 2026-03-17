package io.github.sukjoonhong.pointledger.application.service;

import io.github.sukjoonhong.pointledger.config.PointPolicyManager;
import io.github.sukjoonhong.pointledger.domain.dto.*;
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

@Service
@RequiredArgsConstructor
public class PointAdminService {

    private final Logger logger = LoggerFactory.getLogger(PointAdminService.class);

    private final PointWalletRepository walletRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointAssetRepository assetRepository;
    private final PointBusinessRouter businessRouter;
    private final PointPolicyManager policyManager;

    @Transactional
    public PointResponse issuePoints(PointEarnRequest request) {
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

        logger.info("[ADMIN_EARN] PointKey: {}, Amount: {}, NewBalance: {}",
                tx.getPointKey(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "Points issued successfully.");
    }

    @Transactional
    public PointResponse revertEarn(PointCancelEarnRequest request) {
        PointWallet wallet = getOrCreateWalletWithLock(request.memberId());
        long nextSeq = wallet.getLastSequenceNum() + 1;

        PointAsset originalAsset = assetRepository.findByPointKey(request.originalPointKey())
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "Original asset not found. PointKey: " + request.originalPointKey()));

        PointTransaction tx = saveTransaction(
                request.memberId(),
                originalAsset.getAmount(),
                request.pointKey(),
                request.originalPointKey(),
                nextSeq,
                PointTransactionType.CANCEL_EARN,
                originalAsset.getSource(),
                "Revert earn: " + request.originalPointKey(),
                null
        );

        businessRouter.route(wallet, tx);
        wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
        walletRepository.save(wallet);

        logger.info("[ADMIN_REVERT_EARN] PointKey: {}, OriginalKey: {}, RevertedAmount: {}, NewBalance: {}",
                tx.getPointKey(), request.originalPointKey(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "Earn transaction reverted successfully.");
    }

    @Transactional
    public PointResponse deductPoints(PointUseRequest request) {
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

        logger.info("[ADMIN_DEDUCT] PointKey: {}, OrderId: {}, Amount: {}, NewBalance: {}",
                tx.getPointKey(), request.orderId(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "Points deducted successfully.");
    }

    @Transactional
    public PointResponse revertUse(PointCancelUseRequest request) {
        PointWallet wallet = getOrCreateWalletWithLock(request.memberId());
        long nextSeq = wallet.getLastSequenceNum() + 1;

        PointTransaction tx = saveTransaction(
                request.memberId(),
                request.amount(),
                request.pointKey(),
                request.pointKey(),
                nextSeq,
                PointTransactionType.CANCEL_USE,
                PointSource.ORDER,
                null,
                request.orderId()
        );

        businessRouter.route(wallet, tx);
        wallet.apply(tx, policyManager.getMaxFreePointHoldingLimit());
        walletRepository.save(wallet);

        logger.info("[ADMIN_REVERT_USE] PointKey: {}, OrderId: {}, RefundedAmount: {}, NewBalance: {}",
                tx.getPointKey(), request.orderId(), tx.getAmount(), wallet.getBalance());

        return toResponse(tx, wallet, "Use transaction reverted successfully.");
    }

    @Transactional(readOnly = true)
    public PointResponse getBalance(Long memberId) {
        PointWallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new PointLedgerException(PointErrorCode.ASSET_NOT_FOUND,
                        "Wallet not found for MemberId: " + memberId));

        return PointResponse.builder()
                .memberId(wallet.getMemberId())
                .balance(wallet.getBalance())
                .type("BALANCE")
                .message("Balance retrieved successfully.")
                .build();
    }

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
                            "Failed to recover from wallet creation conflict. MemberId: " + memberId));
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