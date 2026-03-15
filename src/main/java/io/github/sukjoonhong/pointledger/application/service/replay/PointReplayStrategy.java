package io.github.sukjoonhong.pointledger.application.service.replay;

import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointWallet;
import java.util.List;

public interface PointReplayStrategy {
    /**
     * Replays transactions on the given wallet to restore or verify state.
     */
    void replay(PointWallet wallet, List<PointTransaction> transactions);
}