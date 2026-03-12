package io.github.sukjoonhong.pointledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
public class PointLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PointLedgerApplication.class, args);
    }

}
