package io.github.sukjoonhong.pointledger.infrastructure;

import io.github.sukjoonhong.pointledger.domain.entity.Member;
import io.github.sukjoonhong.pointledger.domain.entity.Orders;
import io.github.sukjoonhong.pointledger.repository.MemberRepository;
import io.github.sukjoonhong.pointledger.repository.OrdersRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(LocalDataInitializer.class);
    private final MemberRepository memberRepository;
    private final OrdersRepository ordersRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        logger.info("[DATA_INIT] Starting automatic data generation for local environment.");

        for (int i = 1; i <= 100; i++) {
            Member member = memberRepository.save(Member.builder()
                    .loginId("user_" + i)
                    .build());

            ordersRepository.save(Orders.builder()
                    .memberId(member.getId())
                    .orderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .build());
        }

        logger.info("[DATA_INIT] Successfully generated 100 members and orders.");
    }
}