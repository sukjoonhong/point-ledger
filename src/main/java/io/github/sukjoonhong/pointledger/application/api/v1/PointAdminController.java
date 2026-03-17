package io.github.sukjoonhong.pointledger.application.api.v1;

import io.github.sukjoonhong.pointledger.application.service.PointAdminService;
import io.github.sukjoonhong.pointledger.domain.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative Synchronous API.
 * Provides direct control over point transactions for back-office or management purposes.
 */
@Profile({"api"})
@RestController
@RequestMapping("/v1/admin/points")
@RequiredArgsConstructor
public class PointAdminController {
    private final PointAdminService adminService;

    /**
     * Issue points to a member.
     */
    @PostMapping("/issue")
    public ResponseEntity<PointResponse> issuePoints(@Valid @RequestBody PointEarnRequest request) {
        return ResponseEntity.ok(adminService.issuePoints(request));
    }

    /**
     * Revert a previous earn transaction.
     */
    @PostMapping("/revert-issue")
    public ResponseEntity<PointResponse> revertIssue(@Valid @RequestBody PointCancelEarnRequest request) {
        return ResponseEntity.ok(adminService.revertEarn(request));
    }

    /**
     * Forcefully deduct points from a member.
     */
    @PostMapping("/deduct")
    public ResponseEntity<PointResponse> deductPoints(@Valid @RequestBody PointUseRequest request) {
        return ResponseEntity.ok(adminService.deductPoints(request));
    }

    /**
     * Revert a previous use/deduction transaction.
     */
    @PostMapping("/revert-deduction")
    public ResponseEntity<PointResponse> revertDeduction(@Valid @RequestBody PointCancelUseRequest request) {
        return ResponseEntity.ok(adminService.revertUse(request));
    }
}