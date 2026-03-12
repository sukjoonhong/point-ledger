package io.github.sukjoonhong.pointledger.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

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
}