package io.github.sukjoonhong.pointledger.application.service;

import io.github.sukjoonhong.pointledger.domain.entity.PointAsset;
import io.github.sukjoonhong.pointledger.domain.entity.PointTransaction;
import io.github.sukjoonhong.pointledger.domain.entity.PointUsageDetail;
import io.github.sukjoonhong.pointledger.domain.type.PointUsageStatus;
import io.github.sukjoonhong.pointledger.support.BusinessTimeProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PointAssetManager {

    /**
     * Logic for deducting points from available assets.
     */
    public DeductionResult deduct(PointTransaction tx, List<PointAsset> availableAssets) {
        long remainToDeduct = tx.getAmount();
        List<PointUsageDetail> details = new ArrayList<>();
        List<PointAsset> modifiedAssets = new ArrayList<>();

        for (PointAsset asset : availableAssets) {
            if (remainToDeduct <= 0) break;

            long deductAmount = Math.min(asset.getRemainingAmount(), remainToDeduct);
            asset.deduct(deductAmount);
            remainToDeduct -= deductAmount;

            modifiedAssets.add(asset);
            details.add(PointUsageDetail.builder()
                    .transactionId(tx.getId())
                    .pointAssetId(asset.getId())
                    .orderId(tx.getOrderId())
                    .amountUsed(deductAmount)
                    .amountRefunded(0L)
                    .status(PointUsageStatus.USED)
                    .build());
        }

        return new DeductionResult(modifiedAssets, details, remainToDeduct);
    }

    /**
     * Logic for calculating refund amounts and identifying expired assets.
     */
    public List<RefundItem> calculateRefund(PointTransaction tx, List<PointUsageDetail> usageDetails,
                                            java.util.function.Function<Long, PointAsset> assetLoader,
                                            BusinessTimeProvider timeProvider) {
        long remainRefundAmount = tx.getAmount();
        List<RefundItem> items = new ArrayList<>();

        for (PointUsageDetail detail : usageDetails) {
            if (remainRefundAmount <= 0) break;

            long refundable = detail.getAmountUsed() - detail.getAmountRefunded();
            if (refundable <= 0) continue;

            long amountToRefund = Math.min(refundable, remainRefundAmount);
            PointAsset asset = assetLoader.apply(detail.getPointAssetId());

            items.add(new RefundItem(asset, detail, amountToRefund, asset.isExpired(timeProvider)));
            remainRefundAmount -= amountToRefund;
        }

        return items;
    }

    public record DeductionResult(List<PointAsset> modifiedAssets,
                                  List<PointUsageDetail> usageDetails,
                                  long remainingDeficit) {
    }

    public record RefundItem(PointAsset asset,
                             PointUsageDetail detail,
                             long amount,
                             boolean expired) {
    }
}