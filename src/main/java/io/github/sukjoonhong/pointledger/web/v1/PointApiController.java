package io.github.sukjoonhong.pointledger.web.v1;

import io.github.sukjoonhong.pointledger.service.PointFacadeService;
import io.github.sukjoonhong.pointledger.web.v1.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 동기 처리 API.
 * 모든 요청은 즉시 처리되어 결과를 반환합니다.
 */
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointApiController {

    private final PointFacadeService facadeService;

    /**
     * 포인트 적립
     */
    @PostMapping("/earn")
    public ResponseEntity<PointResponse> earn(@Valid @RequestBody PointEarnRequest request) {
        return ResponseEntity.ok(facadeService.earn(request));
    }

    /**
     * 적립 취소 — 사용된 적립은 취소 불가
     */
    @PostMapping("/earn/cancel")
    public ResponseEntity<PointResponse> cancelEarn(@Valid @RequestBody PointCancelEarnRequest request) {
        return ResponseEntity.ok(facadeService.cancelEarn(request));
    }

    /**
     * 포인트 사용 — 관리자 수기지급(ADMIN) 우선 차감, 만료일 짧은 순
     */
    @PostMapping("/use")
    public ResponseEntity<PointResponse> use(@Valid @RequestBody PointUseRequest request) {
        return ResponseEntity.ok(facadeService.use(request));
    }

    /**
     * 사용 취소 — 전체 또는 부분 취소, 만료 포인트는 신규 적립 처리
     */
    @PostMapping("/use/cancel")
    public ResponseEntity<PointResponse> cancelUse(@Valid @RequestBody PointCancelUseRequest request) {
        return ResponseEntity.ok(facadeService.cancelUse(request));
    }

    /**
     * 잔액 조회
     */
    @GetMapping("/{memberId}/balance")
    public ResponseEntity<PointResponse> getBalance(@PathVariable Long memberId) {
        return ResponseEntity.ok(facadeService.getBalance(memberId));
    }
}
