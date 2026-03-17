package io.github.sukjoonhong.pointledger.application.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    /**
     * 서버 가용성 확인을 위한 Health Check 엔드포인트
     * 호출 경로: GET /health
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/")
    public String index() {
        return "Point Ledger System is Running";
    }
}