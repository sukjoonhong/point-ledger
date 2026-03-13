package io.github.sukjoonhong.pointledger.service.replay;

import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import io.github.sukjoonhong.pointledger.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointReplayService {
    private final PointTransactionRepository transactionRepository;
    private final PointReplayStrategy defaultStrategy;

    @Transactional
    public void performReplay(PointWallet wallet, PointReplayStrategy strategy) {
        PointReplayStrategy activeStrategy = (strategy != null) ? strategy : defaultStrategy;

        final var transactions = transactionRepository.findTransactionsAfterSeq(
                wallet.getMemberId(),
                wallet.getLastSequenceNum()
        );

        activeStrategy.replay(wallet, transactions);
    }
}