package io.github.sukjoonhong.pointledger.service;

import io.github.sukjoonhong.pointledger.domain.entity.PointTask;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PointWalletUpdater {

    private final PointWalletRepository walletRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBalanceUpdate(PointTask task) {
        // Access ledger data via reference
        PointTransaction tx = task.getTransaction();

        // 1. Get wallet with pessimistic lock
        PointWallet wallet = walletRepository.findByMemberIdWithLock(tx.getMemberId())
                .orElseGet(() -> walletRepository.save(new PointWallet(tx.getMemberId(), 0L)));

        // 2. Sequence check
        if (tx.getSequenceNum() <= wallet.getLastSequenceNum()) {
            return;
        }

        // 3. Business logic (Earning/Usage) will be implemented here
        // ...

        // 4. Update wallet sequence
        wallet.updateSequence(tx.getSequenceNum());
    }
}