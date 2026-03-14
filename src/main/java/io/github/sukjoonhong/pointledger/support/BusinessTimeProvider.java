package io.github.sukjoonhong.pointledger.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
@RequiredArgsConstructor
public class BusinessTimeProvider {

    private final Clock clock;

    public Instant nowInstant() {
        return Instant.now(clock);
    }

    public LocalDateTime nowDateTime() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZonedDateTime nowZoned() {
        return ZonedDateTime.now(clock);
    }

    public OffsetDateTime nowOffset() {
        return OffsetDateTime.now(clock);
    }
}