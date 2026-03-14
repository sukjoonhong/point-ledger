package io.github.sukjoonhong.pointledger.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "point-ledger.policy")
@Getter
@Setter
public class PointPolicyManager {
    private Long minEarnLimit;
    private Long maxEarnLimit;
    private Long maxFreePointHoldingLimit;
    private Integer expireDays;
}