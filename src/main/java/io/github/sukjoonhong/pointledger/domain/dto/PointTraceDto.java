package io.github.sukjoonhong.pointledger.domain.dto;

import io.github.sukjoonhong.pointledger.domain.type.PointSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

public class PointTraceDto {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MemberPointSummaryResponse {
        private Long memberId;
        private Long currentBalance;
        private Integer activeAssetCount;
        private List<AssetSummary> activeAssets;
        private org.springframework.data.domain.Page<TransactionSummary> recentTransactions;

        @Getter @Builder
        public static class AssetSummary {
            private Long assetId;
            private String pointKey;
            private Long remainingAmount;
            private String status;
            private java.time.OffsetDateTime expirationDate;
        }

        @Getter @Builder
        public static class TransactionSummary {
            private String pointKey;
            private String type;
            private Long amount;
            private String orderId;
            private java.time.OffsetDateTime createdAt;
        }
    }

    /**
     * 특정 적립 자산의 사용 현황 (적립 기준 추적)
     */
    @Getter
    @Builder
    public static class AssetTraceResponse {
        private Long assetId;
        private PointSource source;        // ADMIN, ORDER 등 식별 가능
        private Long totalAmount;          // 최초 적립액
        private Long remainingAmount;      // 현재 잔액
        private OffsetDateTime expirationDate;
        private List<UsageHistory> usages; // 1원 단위 사용처 목록
    }

    /**
     * 특정 주문에서 사용된 포인트 내역 (주문 기준 추적)
     */
    @Getter
    @Builder
    public static class OrderUsageResponse {
        private String orderId;
        private Long totalUsedAmount;      // 해당 주문에서 쓴 총액
        private List<UsedAssetDetail> details; // 어떤 자산들에서 끌어왔는가
    }

    @Getter
    @Builder
    public static class UsageHistory {
        private String orderId;
        private Long amountUsed;
        private OffsetDateTime usedAt;
    }

    @Getter
    @Builder
    public static class UsedAssetDetail {
        private Long assetId;
        private PointSource source;
        private Long amountFromThisAsset;
    }
}