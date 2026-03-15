package io.github.sukjoonhong.pointledger.application.api.v1;

import io.github.sukjoonhong.pointledger.domain.dto.PointTraceDto.*;
import io.github.sukjoonhong.pointledger.application.service.PointQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile({"!worker & !scheduler"})
@RestController
@RequestMapping("/api/v1/points/tracing")
@RequiredArgsConstructor
public class PointQueryController {

    private final PointQueryService queryService;

    /**
     * 특정 적립 포인트(Asset)의 사용 경로 추적
     */
    @GetMapping("/asset/{assetId}")
    public ResponseEntity<AssetTraceResponse> traceAsset(@PathVariable Long assetId) {
        return ResponseEntity.ok(queryService.traceAssetUsage(assetId));
    }

    /**
     * 특정 주문에서 사용된 포인트의 출처 추적
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<OrderUsageResponse> traceOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(queryService.getOrderUsageTracing(orderId));
    }
}