package io.github.sukjoonhong.pointledger.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
@RequiredArgsConstructor
public class BusinessTimeProvider {

    private final Clock clock;

    public OffsetDateTime nowOffset() {
        return OffsetDateTime.now(clock);
    }
}