package io.github.sukjoonhong.pointledger.application.api.v1;

import io.github.sukjoonhong.pointledger.domain.dto.PointResponse;
import io.github.sukjoonhong.pointledger.application.service.PointQueryService;
import io.github.sukjoonhong.pointledger.domain.dto.PointTraceDto.AssetTraceResponse;
import io.github.sukjoonhong.pointledger.domain.dto.PointTraceDto.MemberPointSummaryResponse;
import io.github.sukjoonhong.pointledger.domain.dto.PointTraceDto.OrderUsageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"api"})
@RestController
@RequestMapping("/v1/points/tracing")
@RequiredArgsConstructor
public class PointQueryController {

    private final PointQueryService queryService;

    /**
     * Retrieve current balance for a member.
     */
    @GetMapping("/members/{memberId}/balance")
    public ResponseEntity<PointResponse> getBalance(@PathVariable Long memberId) {
        return ResponseEntity.ok(queryService.getBalance(memberId));
    }

    /**
     * 특정 적립 포인트(Asset)의 사용 경로 추적
     */
    @GetMapping("/assets/{assetId}")
    public ResponseEntity<AssetTraceResponse> traceAsset(@PathVariable Long assetId) {
        return ResponseEntity.ok(queryService.traceAssetUsage(assetId));
    }

    /**
     * 특정 주문에서 사용된 포인트의 출처 추적
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderUsageResponse> traceOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(queryService.getOrderUsageTracing(orderId));
    }

    @GetMapping("/members/{memberId}/summary")
    public ResponseEntity<MemberPointSummaryResponse> getMemberSummary(
            @PathVariable Long memberId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(queryService.getMemberPointSummary(memberId, pageable));
    }
}